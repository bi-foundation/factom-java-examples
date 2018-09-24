package api.factom.demo.sphereon.com.factomapidemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.blockchain_innovation.factom.client.api.FactomResponse;
import org.blockchain_innovation.factom.client.api.errors.FactomException;
import org.blockchain_innovation.factom.client.api.errors.FactomRuntimeException;
import org.blockchain_innovation.factom.client.api.model.Address;
import org.blockchain_innovation.factom.client.api.model.Chain;
import org.blockchain_innovation.factom.client.api.model.Entry;
import org.blockchain_innovation.factom.client.api.model.response.factomd.CommitChainResponse;
import org.blockchain_innovation.factom.client.api.model.response.factomd.CommitEntryResponse;
import org.blockchain_innovation.factom.client.api.model.response.factomd.EntryTransactionResponse;
import org.blockchain_innovation.factom.client.api.model.response.factomd.RevealResponse;
import org.blockchain_innovation.factom.client.api.model.response.walletd.ComposeResponse;
import org.blockchain_innovation.factom.client.api.settings.RpcSettings;
import org.blockchain_innovation.factom.client.impl.FactomdClientImpl;
import org.blockchain_innovation.factom.client.impl.WalletdClientImpl;
import org.blockchain_innovation.factom.client.impl.json.gson.JsonConverterGSON;
import org.blockchain_innovation.factom.client.impl.settings.RpcSettingsImpl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    protected static final String EC_PUBLIC_ADDRESS = "EC2hYEMoqp5zU45vS1yEiqrfs3CLCrLHScU8e9haM7oAQ3KvbPmn";

    private static final String TAG = "FactomAPIDemo";

    private static final JsonConverterGSON CONV = new JsonConverterGSON();

    protected final FactomdClientImpl factomdClient = new FactomdClientImpl();
    protected final WalletdClientImpl walletdClient = new WalletdClientImpl();

    private ProgressBar progressBar;
    private TextView logView;
    private Address address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        Button button = findViewById(R.id.button);
        button.setOnClickListener(this);
        logView = findViewById(R.id.logView);
        logView.setMovementMethod(new ScrollingMovementMethod());

        logView.setText("init application");
        progressBar.setVisibility(View.GONE);

        Properties properties = new Properties();
        properties.setProperty("factomd.url", "http://136.144.204.97:8088/v2");
        properties.setProperty("walletd.url", "http://136.144.204.97:8089/v2");
        address = new Address(EC_PUBLIC_ADDRESS);

        factomdClient.setSettings(new RpcSettingsImpl(RpcSettings.SubSystem.FACTOMD, properties));
        walletdClient.setSettings(new RpcSettingsImpl(RpcSettings.SubSystem.WALLETD, properties));
    }

    @Override
    public void onClick(View v) {
        new StuffTask(progressBar, logView).execute();
    }

    protected Chain chain() {
        String randomness = new Date().toString();
        List<String> externalIds = Arrays.asList(
                "ChainEntryIT",
                randomness
        );

        Entry firstEntry = new Entry();
        firstEntry.setExternalIds(externalIds);
        firstEntry.setContent("ChainEntry integration test content");

        Chain chain = new Chain();
        chain.setFirstEntry(firstEntry);
        return chain;
    }

    protected Entry entry(String chainId) {
        List<String> externalIds = Arrays.asList("Entry ExtID 1", "Entry ExtID 2");

        Entry entry = new Entry();
        entry.setChainId(chainId);
        entry.setContent("Entry content");
        entry.setExternalIds(externalIds);
        return entry;
    }

    private class StuffTask extends AsyncTask<String, String, String> {

        private final ProgressBar progressBar;
        private final TextView logView;

        public StuffTask(ProgressBar progressBar, TextView logView) {
            this.progressBar = progressBar;
            this.logView = logView;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);

            logView.append("\nwe are going to do stuff");
            Log.i(TAG, "we are going to do stuff");
        }

        @Override
        protected String doInBackground(String... args) {
            Log.i(TAG, "doing background stuff");

            try {
                Chain chain = chain();
                FactomResponse<ComposeResponse> composeChainResponse = walletdClient.composeChain(chain, address).join();

                if (composeChainResponse.hasErrors()) {
                    return "we failed composing chain :(";
                }

                ComposeResponse composeChain = composeChainResponse.getResult();
                String commitChainParam = composeChain.getCommit().getParams().getMessage();
                String revealChainParam = composeChain.getReveal().getParams().getEntry();

                Log.i(TAG, "compose chain: commit=" + commitChainParam + ", reveal=" + revealChainParam);
                publishProgress("compose chain: commit=" + commitChainParam + ", reveal=" + revealChainParam);

                FactomResponse<CommitChainResponse> commitChainResponse = factomdClient.commitChain(commitChainParam).join();
                if (commitChainResponse.hasErrors()) {
                    return "we failed to commit chain :(";
                }

                CommitChainResponse commitChain = commitChainResponse.getResult();
                Log.i(TAG, "Committing chain: status=" + commitChain.getMessage() + ", chain=" + commitChain.getChainIdHash() + ", entry=" + commitChain.getEntryHash());
                publishProgress("Committing chain: status=" + commitChain.getMessage() + ", chain=" + commitChain.getChainIdHash() + ", entry=" + commitChain.getEntryHash());

                FactomResponse<RevealResponse> revealChainResponse = factomdClient.revealChain(revealChainParam).join();
                if (revealChainResponse.hasErrors()) {
                    return "we failed revealing chain :(";
                }

                RevealResponse revealChain = revealChainResponse.getResult();
                Log.i(TAG, "Revealing chain: status=" + revealChain.getMessage() + ", chain=" + revealChain.getChainId() + ", entry=" + revealChain.getEntryHash());
                publishProgress("Revealing chain: status=" + revealChain.getMessage() + ", chain=" + revealChain.getChainId() + ", entry=" + revealChain.getEntryHash());

                waitOnConfirmation(revealChain.getChainId(), revealChain.getEntryHash(), EntryTransactionResponse.Status.TransactionACK, 15);

                Entry entry = entry(revealChain.getChainId());
                FactomResponse<ComposeResponse> composeEntryResponse = walletdClient.composeEntry(entry, address).join();

                if (composeEntryResponse.hasErrors()) {
                    return "we failed composing entry :(";
                }

                ComposeResponse composeEntry = composeEntryResponse.getResult();
                String commitEntryParam = composeEntry.getCommit().getParams().getMessage();
                String revealEntryParam = composeEntry.getReveal().getParams().getEntry();

                Log.i(TAG, "compose entry: commit=" + commitEntryParam + ", reveal=" + revealEntryParam);
                publishProgress("compose entry: commit=" + commitEntryParam + ", reveal=" + revealEntryParam);

                FactomResponse<CommitEntryResponse> commitEntryResponse = factomdClient.commitEntry(commitEntryParam).join();
                if (commitEntryResponse.hasErrors()) {
                    return "we failed to commit entry :(";
                }
                CommitEntryResponse commitEntry = commitEntryResponse.getResult();
                Log.i(TAG, "Committing entry: status=" + commitEntry.getMessage() + ", entry=" + commitEntry.getEntryHash());
                publishProgress("Committing entry: status=" + commitEntry.getMessage() + ", entry=" + commitEntry.getEntryHash());

                FactomResponse<RevealResponse> revealEntryResponse = factomdClient.revealEntry(revealEntryParam).join();
                if (revealEntryResponse.hasErrors()) {
                    return "we failed revealing :(";
                }
                RevealResponse revealEntry = revealEntryResponse.getResult();
                Log.i(TAG, "Revealing entry: status=" + revealEntry.getMessage() + ", chain=" + revealEntry.getChainId() + ", entry=" + revealEntry.getEntryHash());
                publishProgress("Revealing entry: status=" + revealEntry.getMessage() + ", chain=" + revealEntry.getChainId() + ", entry=" + revealEntry.getEntryHash());

                waitOnConfirmation(revealEntry.getChainId(), revealEntry.getEntryHash(), EntryTransactionResponse.Status.TransactionACK, 15);

                return "done the stuff... :)";
            } catch (FactomRuntimeException e) {
                Log.e(TAG, "something failed: " + e.getMessage(), e);
                return "an exception :'(" + e.getMessage();
            } catch (Exception e) {
                Log.e(TAG, "this shouldn't happen: " + e.getMessage(), e);
                return "an exception :'(" + e.getMessage();
            }
        }

        private boolean waitOnConfirmation(String chainId, String entryHash, EntryTransactionResponse.Status desiredStatus, int maxSeconds) throws InterruptedException, FactomException.ClientException {
            int seconds = 0;
            while (seconds < maxSeconds) {
                publishProgress("At verification second: " + seconds);
                FactomResponse<EntryTransactionResponse> transactionsResponse = factomdClient.ackTransactions(entryHash, chainId, EntryTransactionResponse.class).join();
                if (!transactionsResponse.hasErrors()) {
                    EntryTransactionResponse entryTransaction = transactionsResponse.getResult();
                    publishProgress("---");
                    EntryTransactionResponse.Status status = entryTransaction.getCommitData().getStatus();
                    if (seconds > 12 && seconds % 6 == 0 && EntryTransactionResponse.Status.TransactionACK != status) {
                        Log.e(TAG, "Transaction still not in desired status after: " + seconds + ", State: " + status + ". Probably will not succeed!");
                        publishProgress("Transaction still not in desired status after: " + seconds + ", State: " + status + ". Probably will not succeed!");
                    } else if (desiredStatus == status) {
                        publishProgress("Transaction in desired status after: " + seconds + ", State: " + status + ".");
                        return true;
                    }
                }
                seconds++;
                Thread.sleep(1000);
            }
            return false;
        }

        @Override
        protected void onProgressUpdate(String... info) {
            for (String str : info) {
                logView.append("\n>  " + str);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);
            logView.append("\nfinished doing stuff: " + result);
            Log.i(TAG, "finished: " + result);
        }
    }
}

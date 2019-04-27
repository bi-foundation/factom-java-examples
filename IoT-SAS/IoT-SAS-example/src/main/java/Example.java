import org.blockchain_innovation.factom.client.api.EntryApi;
import org.blockchain_innovation.factom.client.api.WalletdClient;
import org.blockchain_innovation.factom.client.api.model.Address;
import org.blockchain_innovation.factom.client.api.model.Chain;
import org.blockchain_innovation.factom.client.api.model.Entry;
import org.blockchain_innovation.factom.client.api.model.response.CommitAndRevealChainResponse;
import org.blockchain_innovation.factom.client.api.model.response.CommitAndRevealEntryResponse;
import org.blockchain_innovation.factom.client.api.settings.RpcSettings;
import org.blockchain_innovation.factom.client.impl.EntryApiImpl;
import org.blockchain_innovation.factom.client.impl.FactomdClientImpl;
import org.blockchain_innovation.factom.client.impl.OfflineWalletdClientImpl;
import org.blockchain_innovation.factom.client.impl.settings.RpcSettingsImpl;
import org.blockchain_innovation.factom.iot_sas.IoTSASClientImpl;
import org.blockchain_innovation.factom.iot_sas.IoTSASPort;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

/**
 * Example SAS Java application that reads the public EC address, creates a chain and adds an entry
 */
public class Example {
    private final IoTSASClientImpl sas = new IoTSASClientImpl();
    private final IoTSASPort port = new IoTSASPort();
    private final FactomdClientImpl factomdClient = new FactomdClientImpl();
    private final WalletdClient signingClient = new OfflineWalletdClientImpl();
    private final EntryApi entryApi = new EntryApiImpl();


    private Example(String portName, int baudRate) throws IOException {
        port.setup(portName, baudRate);
        sas.setPort(port);
        factomdClient.lowLevelClient().setSettings(new RpcSettingsImpl(RpcSettings.SubSystem.FACTOMD, getProperties()));
        signingClient.lowLevelClient().setSettings(new RpcSettingsImpl(RpcSettings.SubSystem.WALLETD, getProperties()));
        entryApi.setFactomdClient(factomdClient).setWalletdClient(signingClient);
    }


    public static void main(String[] args) throws IOException {
        String portName = "/dev/serial0";
        int baudRate = 57600;
        if (args != null && args.length >= 1) {
            portName = args[0];
            if (args.length > 1) {
                baudRate = Integer.parseInt(args[1]);
            }
        }
        new Example(portName, baudRate).execute();
        System.exit(0);
    }


    protected void execute() {
        // Retrieve the public EC address and balance from the SAS device
        Address ECAddress = sas.getPublicECAddress();

        long balance = factomdClient.entryCreditBalance(ECAddress).join().getResult().getBalance();
        System.out.println(String.format("Public hardware EC address: %s, contains %d ECs", ECAddress, balance));
        if (balance < 12) {
            System.err.println(String.format("Please top up EC balance for %s, by going to %s", ECAddress.getValue(), "https://faucet.factoid.org"));
        }

        // Create a device (EC address) specific example chain
        CommitAndRevealChainResponse commitAndRevealChain = entryApi.commitAndRevealChain(chain(String.format("SAS chain example for %s", ECAddress), "SAS", "Java", "Example", ECAddress.getValue()), sas).join();
        System.out.println(String.format("Chain with Id '%s' and first entry hash '%s' created", commitAndRevealChain.getRevealResponse().getChainId(), commitAndRevealChain.getRevealResponse().getEntryHash()));
        System.out.println(String.format("Please visit the explorer at: https://testnet.factoid.org/entry?hash=%s", commitAndRevealChain.getRevealResponse().getEntryHash()));

        // Create an entry in the chain
        CommitAndRevealEntryResponse commitAndRevealEntry = entryApi.commitAndRevealEntry(entry(commitAndRevealChain.getRevealResponse().getChainId(), String.format("SAS entry example for %s", ECAddress), "Timestamp", "" + System.currentTimeMillis()), sas).join();
        System.out.println(String.format("Chain with Id '%s' now has an additional entry with hash '%s'", commitAndRevealEntry.getRevealResponse().getChainId(), commitAndRevealEntry.getRevealResponse().getEntryHash()));
        System.out.println(String.format("Please visit the explorer at: https://testnet.factoid.org/entry?hash=%s", commitAndRevealEntry.getRevealResponse().getEntryHash()));

        balance = factomdClient.entryCreditBalance(ECAddress).join().getResult().getBalance();
        System.out.println(String.format("Public hardware EC address: %s, now contains %d ECs", ECAddress, balance));



    }

    protected Chain chain(String content, String... externalIds) {
        return new Chain().setFirstEntry(entry(null, content, externalIds));
    }

    protected Entry entry(String chainId, String content, String... externalIds) {
        Entry entry = new Entry();
        entry.setChainId(chainId);
        entry.setContent(content);
        entry.setExternalIds(Arrays.asList(externalIds));
        return entry;
    }

    protected Properties getProperties() throws IOException {
        Properties properties = new Properties();
        InputStream is = getClass().getClassLoader().getResourceAsStream("settings.properties");
        properties.load(is);
        is.close();
        return properties;
    }
}

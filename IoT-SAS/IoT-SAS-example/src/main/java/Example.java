import org.blockchain_innovation.factom.client.api.EntryApi;
import org.blockchain_innovation.factom.client.api.FactomdClient;
import org.blockchain_innovation.factom.client.api.WalletdClient;
import org.blockchain_innovation.factom.client.api.model.Address;
import org.blockchain_innovation.factom.client.api.model.Chain;
import org.blockchain_innovation.factom.client.api.model.Entry;
import org.blockchain_innovation.factom.client.api.model.response.CommitAndRevealChainResponse;
import org.blockchain_innovation.factom.client.api.model.response.CommitAndRevealEntryResponse;
import org.blockchain_innovation.factom.client.impl.EntryApiImpl;
import org.blockchain_innovation.factom.client.impl.FactomdClientImpl;
import org.blockchain_innovation.factom.client.impl.OfflineWalletdClientImpl;
import org.blockchain_innovation.factom.iot_sas.IoTSASClientImpl;
import org.blockchain_innovation.factom.iot_sas.IoTSASPort;

import java.util.Arrays;

/**
 * Example SAS Java application that reads the public EC address, creates a chain and adds an entry
 */
public class Example {
    private final IoTSASClientImpl sas = new IoTSASClientImpl();
    private final IoTSASPort port = new IoTSASPort();
    private final FactomdClient factomdClient = new FactomdClientImpl();
    private final WalletdClient signingClient = new OfflineWalletdClientImpl();
    private final EntryApi entryApi = new EntryApiImpl();


    private Example() {
        port.setup("/dev/serial0", 57600);
        sas.setPort(port);
        entryApi.setFactomdClient(factomdClient).setWalletdClient(signingClient);
    }


    public static void main(String[] args) {
        new Example().execute();
    }


    protected void execute() {
        // Retrieve the public EC address and balance from the SAS device
        Address ECAddress = sas.getPublicECAddress();
        System.out.println(String.format("Public hardware EC address: %s, contains %d ECs", ECAddress, factomdClient.entryCreditBalance(ECAddress).join().getResult().getBalance()));


        // Create a device (EC address) specific example chain
        CommitAndRevealChainResponse commitAndRevealChain = entryApi.commitAndRevealChain(chain(String.format("SAS chain example for %s", ECAddress), "SAS", "Java", "Example", ECAddress.getValue()), sas).join();
        System.out.println(String.format("Chain with Id '%s' and first entry hash '%s' created", commitAndRevealChain.getRevealResponse().getChainId(), commitAndRevealChain.getRevealResponse().getEntryHash()));
        System.out.println(String.format("Please visit the explorer at: https://explorer.factoid.org/entry?hash=%s", commitAndRevealChain.getRevealResponse().getEntryHash()));

        // Create an entry in the chain
        CommitAndRevealEntryResponse commitAndRevealEntry = entryApi.commitAndRevealEntry(entry(commitAndRevealChain.getRevealResponse().getChainId(), String.format("SAS entry example for %s", ECAddress), "Timestamp", "" + System.currentTimeMillis()), sas).join();
        System.out.println(String.format("Chain with Id '%s' now has an additional entry with hash '%s'", commitAndRevealEntry.getRevealResponse().getChainId(), commitAndRevealEntry.getRevealResponse().getEntryHash()));
        System.out.println(String.format("Please visit the explorer at: https://explorer.factoid.org/entry?hash=%s", commitAndRevealEntry.getRevealResponse().getEntryHash()));

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
}

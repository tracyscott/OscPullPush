package art.lookingup;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.transport.OSCPortOut;
import org.json.JSONObject;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class OSCPullPush {
    private static final String SERVICE_TYPE = "_oscjson._tcp.local.";
    private static List<String> oscAddresses;
    private static String oscHost;
    private static int oscPort;
    private static String discoveredHost;
    private static int discoveredPort;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java OSCQueryCommandLine <osc_addresses_file> <osc_host> <osc_port>");
            System.exit(1);
        }

        String oscAddressesFile = args[0];
        oscHost = args[1];
        oscPort = Integer.parseInt(args[2]);

        loadOSCAddresses(oscAddressesFile);
        discoverOSCQueryService();

        if (discoveredHost != null && discoveredPort != 0) {
            String jsonResult = queryOSCService(discoveredHost, discoveredPort);
            processAndSendOSC(jsonResult);
        } else {
            System.out.println("No OSCQuery service found.");
        }
    }

    private static void loadOSCAddresses(String filename) throws IOException {
        oscAddresses = Files.readAllLines(Paths.get(filename));
        System.out.println("Loaded " + oscAddresses.size() + " OSC addresses from " + filename);
    }

    private static void discoverOSCQueryService() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

        jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                // Service added, but not resolved yet
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                // Service removed
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                discoveredHost = event.getInfo().getHostAddresses()[0];
                discoveredPort = event.getInfo().getPort();
                System.out.println("Discovered OSCQuery service at " + discoveredHost + ":" + discoveredPort);
                latch.countDown();
            }
        });

        // Wait for up to 5 seconds for a service to be discovered
        latch.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
        jmdns.close();
    }

    private static String queryOSCService(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("GET / HTTP/1.1");
            out.println("Host: " + host);
            out.println("Connection: close");
            out.println();

            StringBuilder response = new StringBuilder();
            String line;
            boolean headers = true;
            while ((line = in.readLine()) != null) {
                if (headers) {
                    if (line.isEmpty()) {
                        headers = false;
                    }
                } else {
                    response.append(line).append("\n");
                }
            }

            return response.toString().trim();
        } catch (IOException e) {
            System.err.println("Error querying OSC service: " + e.getMessage());
            return null;
        }
    }

    private static void processAndSendOSC(String jsonResult) {
        if (jsonResult == null) {
            System.err.println("No data received from OSCQuery service.");
            return;
        }

        try {
            JSONObject json = new JSONObject(jsonResult);
            OSCPortOut sender = new OSCPortOut(InetAddress.getByName(oscHost), oscPort);

            for (String address : oscAddresses) {
                if (addressExists(json, address)) {
                    Object value = getValueForAddress(json, address);
                    if (value != null) {
                        value = convertToFloat(value);
                        OSCMessage message = new OSCMessage(address, Arrays.asList(value));
                        sender.send(message);
                        System.out.println("Sent OSC message: " + address + " " + value);
                    }
                }
            }

            sender.close();
        } catch (Exception e) {
            System.err.println("Error processing or sending OSC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean addressExists(JSONObject json, String address) {
        String[] parts = address.split("/");
        JSONObject current = json;
        for (int i = 1; i < parts.length; i++) {
            if (!current.has("CONTENTS") || !current.getJSONObject("CONTENTS").has(parts[i])) {
                return false;
            }
            current = current.getJSONObject("CONTENTS").getJSONObject(parts[i]);
        }
        return true;
    }

    private static Object getValueForAddress(JSONObject json, String address) {
        String[] parts = address.split("/");
        JSONObject current = json;
        for (int i = 1; i < parts.length; i++) {
            if (!current.has("CONTENTS") || !current.getJSONObject("CONTENTS").has(parts[i])) {
                return null;
            }
            current = current.getJSONObject("CONTENTS").getJSONObject(parts[i]);
        }
        return current.has("VALUE") ? current.get("VALUE") : null;
    }

    private static Object convertToFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else if (value instanceof String) {
            try {
                return Float.parseFloat((String) value);
            } catch (NumberFormatException e) {
                // If it's not a valid number, return the original string
                return value;
            }
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? 1.0f : 0.0f;
        }
        // For any other type, return the original value
        return value;
    }
}

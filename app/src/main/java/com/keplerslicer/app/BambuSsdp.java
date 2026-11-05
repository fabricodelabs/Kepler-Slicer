package com.keplerslicer.app;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Bambu Lab printers do not use DNS-SD: they announce themselves with SSDP NOTIFY packets (UDP 2021 broadcast and
 * 239.255.255.250:1990), carrying the IP in Location, the serial number in USN and the model in DevModel.bambu.com.
 */
final class BambuSsdp {
    private BambuSsdp() {}

    static final class Device {
        final String ip, serial, name, model;
        Device(String ip, String serial, String name, String model) { this.ip = ip; this.serial = serial; this.name = name; this.model = model; }
    }

    /** Search request that makes printers answer at once instead of on their next periodic announcement. */
    static byte[] search() {
        return ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1990\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: urn:bambulab-com:device:3dprinter:1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    /** A Bambu printer announcement, or null for anything else (TVs, routers, paper printers). */
    static Device parse(byte[] data, int length, String senderIp) {
        String text = new String(data, 0, Math.max(0, Math.min(length, data.length)), StandardCharsets.UTF_8);
        String nt = "", usn = "", location = "", name = "", model = "";
        for (String line : text.split("\r?\n")) {
            int colon = line.indexOf(':'); if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT), value = line.substring(colon + 1).trim();
            switch (key) {
                case "nt": case "st": nt = value; break;
                case "usn": usn = value; break;
                case "location": location = value; break;
                case "devname.bambu.com": name = value; break;
                case "devmodel.bambu.com": model = value; break;
                default: break;
            }
        }
        if (!nt.toLowerCase(Locale.ROOT).contains("bambulab-com:device:3dprinter")) return null;
        String serial = usn.replaceAll("^uuid:", "").replaceAll("::.*$", "").trim();
        if (!serial.matches("[A-Za-z0-9]{8,32}")) return null;
        String ip = location.replaceFirst("^[a-zA-Z]+://", "").replaceAll("[:/].*$", "").trim();
        if (!ip.matches("(\\d{1,3}\\.){3}\\d{1,3}")) ip = senderIp == null ? "" : senderIp;
        if (ip.isEmpty()) return null;
        String label = modelName(model);
        return new Device(ip, serial.toUpperCase(Locale.ROOT), name.isEmpty() ? (label.isEmpty() ? "Bambu Lab" : label) : name, label);
    }

    /** Readable model for the announced model code. */
    static String modelName(String code) {
        switch (code == null ? "" : code.trim().toUpperCase(Locale.ROOT)) {
            case "BL-P001": return "Bambu Lab X1 Carbon";
            case "BL-P002": return "Bambu Lab X1";
            case "C13": return "Bambu Lab X1E";
            case "C11": return "Bambu Lab P1P";
            case "C12": return "Bambu Lab P1S";
            case "N7": return "Bambu Lab P2S";
            case "N2S": return "Bambu Lab A1";
            case "N1": return "Bambu Lab A1 mini";
            case "O1D": return "Bambu Lab H2D";
            case "O1S": return "Bambu Lab H2S";
            default: return code == null || code.trim().isEmpty() ? "" : "Bambu Lab " + code.trim();
        }
    }

    /** Office printers also publish a web page over DNS-SD (_http._tcp); they are not 3D printers. */
    static boolean isPaperPrinter(String serviceName) {
        String n = serviceName == null ? "" : serviceName.toLowerCase(Locale.ROOT);
        return n.matches(".*\\b(hp|hewlett|laserjet|deskjet|officejet|envy|canon|pixma|i-sensys|epson|brother|xerox|kyocera|ricoh|lexmark|samsung|pantum|oki|konica|bizhub|sharp|toshiba|dell|katusha)\\b.*")
            || n.contains("mfp") || n.contains("scanner");
    }
}

package com.keplerslicer.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** JVM unit tests for the Android-free core (run with ./gradlew :core:test). */
public class CoreTest {
    private static final String CRLF = "\r\n";

    // ---- ModelTransform: storage format and sinking ----

    @Test public void transformRoundTripsWithAndWithoutSinking() {
        ModelTransform flat = new ModelTransform(10, 20, 30, 1, 2, 3, 4, 5);
        assertEquals(8, flat.encode().split(",").length);
        ModelTransform back = ModelTransform.decode(flat.encode());
        assertEquals(4, back.offsetX, 1e-9); assertEquals(0, back.offsetZ, 1e-9);

        ModelTransform sunk = new ModelTransform(0, 0, 0, 1, 1, 1, 0, 0, -8.5);
        assertEquals(9, sunk.encode().split(",").length);
        assertEquals(-8.5, ModelTransform.decode(sunk.encode()).offsetZ, 1e-9);
        assertEquals(9, sunk.toNativeArguments().length);
        assertEquals(8, flat.toNativeArguments().length);
    }

    @Test public void transformRefusesFloatingAndBrokenValues() {
        assertThrows(IllegalArgumentException.class, () -> new ModelTransform(0, 0, 0, 1, 1, 1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ModelTransform(0, 0, 0, 0, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModelTransform(0, 0, 0, 1, 1, 1, 20_000, 0));
        assertThrows(IllegalArgumentException.class, () -> ModelTransform.decode("1,2,3"));
    }

    // ---- Bambu SSDP discovery ----

    @Test public void bambuAnnouncementIsParsed() {
        String notify = String.join(CRLF, "NOTIFY * HTTP/1.1", "Location: 192.168.1.110", "NT: urn:bambulab-com:device:3dprinter:1",
            "USN: 01p00c123456789", "DevModel.bambu.com: C12", "DevName.bambu.com: Workshop", "", "");
        byte[] b = notify.getBytes(StandardCharsets.UTF_8);
        BambuSsdp.Device d = BambuSsdp.parse(b, b.length, "192.168.1.110");
        assertNotNull(d);
        assertEquals("192.168.1.110", d.ip); assertEquals("01P00C123456789", d.serial); assertEquals("Bambu Lab P1S", d.model);
    }

    @Test public void otherSsdpDevicesAndOfficePrintersAreIgnored() {
        byte[] tv = String.join(CRLF, "NOTIFY * HTTP/1.1", "NT: urn:dial-multiscreen-org:service:dial:1", "USN: uuid:1", "", "").getBytes(StandardCharsets.UTF_8);
        assertNull(BambuSsdp.parse(tv, tv.length, "192.168.1.2"));
        assertTrue(BambuSsdp.isPaperPrinter("HP LaserJet MFP M140w"));
        assertFalse(BambuSsdp.isPaperPrinter("Voron 2.4 Mainsail"));
    }

    // ---- Profile grouping ----

    @Test public void filamentBrandComesFromTheProfileName() {
        assertEquals("Polymaker", ProfileGroups.filamentBrand("Polymaker PolyLite PLA @Qidi Q2"));
        assertEquals("", ProfileGroups.filamentBrand("Generic PETG @System"));
        assertEquals("", ProfileGroups.filamentBrand("PLA Basic"));
        assertEquals("Bambu Lab", ProfileGroups.printerMaker("BBL"));
    }

    // ---- Model links ----

    @Test public void printablesPagesAreRecognised() {
        assertEquals("3161", ModelLinks.printablesId(URI.create("https://www.printables.com/model/3161-3d-benchy")));
        assertNull(ModelLinks.printablesId(URI.create("https://example.com/model/3161")));
        assertNotNull(ModelLinks.unsupportedPage(URI.create("https://makerworld.com/en/models/1")));
        assertNull(ModelLinks.unsupportedPage(URI.create("https://files.example.com/a.stl")));
    }

    // ---- Printer endpoints ----

    @Test public void endpointValidatesCredentialsByAuthType() {
        assertThrows(IllegalArgumentException.class, () -> new PrinterEndpoint(PrinterEndpoint.Type.OCTOPRINT, "http://192.168.1.5", PrinterEndpoint.Auth.API_KEY, "", "", ""));
        PrinterEndpoint ok = new PrinterEndpoint(PrinterEndpoint.Type.OCTOPRINT, "192.168.1.5", PrinterEndpoint.Auth.API_KEY, "", "key", "");
        assertEquals("192.168.1.5", ok.baseUri.getHost());
        assertArrayEquals(new byte[0], new byte[0]);
    }
}

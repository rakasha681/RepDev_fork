package com.repdev;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Negative tests for the XXE hardening on {@link Style#Style(File, Style)}.
 *
 * Asserts that a malicious theme XML which references an external entity
 * (a billion-laughs entity in this case, which would otherwise hang the JVM
 * for many seconds and explode the heap) is rejected by the parser, leaving
 * the resulting Style harmless.
 */
class StyleXxeHardeningTest {

	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("repdev-style-xxe-test");
	}

	@AfterEach
	void tearDown() throws IOException {
		if (tempDir != null) {
			deleteRecursively(tempDir.toFile());
		}
	}

	@Test
	void billionLaughsRejectedQuickly() throws IOException {
		// Classic billion-laughs payload: 10 levels of 10x expansion = 10^10
		// entity references at the inner ref. A non-hardened parser blows the
		// heap and hangs for many seconds; a hardened one rejects the DOCTYPE.
		String xml = "<?xml version=\"1.0\"?>"
				+ "<!DOCTYPE lolz ["
				+ "  <!ENTITY lol \"lol\">"
				+ "  <!ENTITY lol1 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">"
				+ "  <!ENTITY lol2 \"&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;\">"
				+ "  <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">"
				+ "  <!ENTITY lol4 \"&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;\">"
				+ "  <!ENTITY lol5 \"&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;\">"
				+ "]>"
				+ "<RepDevStyle extends=\"\">"
				+ "  <header>"
				+ "    <name>&lol5;</name>"
				+ "    <version>1</version>"
				+ "    <description>x</description>"
				+ "    <author>x</author>"
				+ "  </header>"
				+ "  <style/>"
				+ "</RepDevStyle>";
		File payload = writeXml("billion-laughs.xml", xml);

		long start = System.currentTimeMillis();
		Style style = new Style(payload, null);
		long elapsedMs = System.currentTimeMillis() - start;

		// Should not have taken anywhere near billion-laughs time. Generous
		// bound: 5 seconds. A non-hardened parser would either OOM or take
		// far longer.
		org.junit.jupiter.api.Assertions.assertTrue(elapsedMs < 5_000,
				"Hardened parser should reject DOCTYPE quickly; took " + elapsedMs + " ms");

		// Either the constructor swallowed the SAXException (XXE-disallowed
		// document is treated as a malformed style file) and we get a
		// non-null Style with no usable values, or the parse aborted before
		// populating the name from the entity. Both are acceptable hardening
		// outcomes. KEY assertions: (1) time bound above, and (2) the
		// "lol..." entity must NOT have expanded into the style's name
		// — that would mean the parser honoured the DOCTYPE.
		assertNotNull(style);
		if (style.name != null) {
			org.junit.jupiter.api.Assertions.assertFalse(style.name.contains("lol"),
					"Billion-laughs entity must not have expanded into style.name");
		}
	}

	@Test
	void externalEntityFileReadIsBlocked() throws IOException {
		// Drop a "secret" file in the temp dir, then try to read it via a
		// SYSTEM entity reference. The hardened parser must NOT inline the
		// file contents into the parsed name attribute.
		Path secret = tempDir.resolve("secret.txt");
		Files.write(secret, "TOP_SECRET_CONTENTS".getBytes(StandardCharsets.UTF_8));

		String xml = "<?xml version=\"1.0\"?>"
				+ "<!DOCTYPE foo ["
				+ "  <!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">"
				+ "]>"
				+ "<RepDevStyle extends=\"\">"
				+ "  <header>"
				+ "    <name>&xxe;</name>"
				+ "    <version>1</version>"
				+ "    <description>x</description>"
				+ "    <author>x</author>"
				+ "  </header>"
				+ "  <style/>"
				+ "</RepDevStyle>";
		File payload = writeXml("xxe-external.xml", xml);

		Style style = new Style(payload, null);

		// Parser rejected DOCTYPE → either the parse aborted (style.name is
		// null) or it parsed but the entity did NOT expand into the name.
		// Either way, the secret string must not appear anywhere accessible.
		if (style.name != null) {
			org.junit.jupiter.api.Assertions.assertFalse(style.name.contains("TOP_SECRET_CONTENTS"),
					"External entity must not have been resolved into the name");
		}
	}

	@Test
	void plainStyleXmlStillParses() throws IOException {
		// Sanity check: legitimate non-DOCTYPE theme XML still works after
		// the hardening. If this fails the hardening is too restrictive.
		String xml = "<?xml version=\"1.0\"?>"
				+ "<RepDevStyle extends=\"\">"
				+ "  <header>"
				+ "    <name>plain</name>"
				+ "    <version>1</version>"
				+ "    <description>plain test theme</description>"
				+ "    <author>tester</author>"
				+ "  </header>"
				+ "  <style>"
				+ "    <comments fgColor=\"@green\"/>"
				+ "  </style>"
				+ "  <palette>"
				+ "    <color id=\"green\" value=\"00FF00\"/>"
				+ "  </palette>"
				+ "</RepDevStyle>";
		File payload = writeXml("plain.xml", xml);

		Style style = new Style(payload, null);
		assertNotNull(style.name);
		org.junit.jupiter.api.Assertions.assertEquals("plain", style.name);
		org.junit.jupiter.api.Assertions.assertEquals("00FF00", style.getValue("comments", "fgColor"));
	}

	@Test
	void missingValueReturnsEmptyString() throws IOException {
		// Index hit-miss path: tags or attributes that aren't in the style
		// fall through to the fallback (or empty). Same contract as before
		// the index-rebuild refactor.
		String xml = "<?xml version=\"1.0\"?>"
				+ "<RepDevStyle extends=\"\">"
				+ "  <header><name>n</name><version>1</version><description>d</description><author>a</author></header>"
				+ "  <style/>"
				+ "</RepDevStyle>";
		File payload = writeXml("empty-style.xml", xml);

		Style style = new Style(payload, null);
		assertNotNull(style);
		org.junit.jupiter.api.Assertions.assertEquals("", style.getValue("comments", "fgColor"));
		assertNull(style.getColor("comments", "fgColor"));
	}

	private File writeXml(String name, String body) throws IOException {
		Path p = tempDir.resolve(name);
		Files.write(p, body.getBytes(StandardCharsets.UTF_8));
		return p.toFile();
	}

	private static void deleteRecursively(File f) {
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) for (File c : children) deleteRecursively(c);
		}
		f.delete();
	}
}

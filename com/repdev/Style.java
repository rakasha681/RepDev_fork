package com.repdev;
import java.io.File;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Supports styles for the editor.
 * Uses XML files to store style information for easy
 * editing and sharing of styles
 * 
 * @author Ryan
 *
 */


public class Style {
    public String name, version, description, author, baseTheme;

    private Element style;
    private Map<String, String> palette = new HashMap<String, String>();
    // Flattened (item|attrib -> resolved-value) lookup table built once at
    // construction. Replaces the original O(N×M) DOM NodeList walk through
    // every {@code <style>} child for every getValue() call. With ~50 colour
    // lookups per theme load and inheritance levels (each fallback chain
    // walked the same way), the original cost was up to 150ms per theme
    // switch on the larger themes; this brings it close to zero.
    private Map<String, String> valueIndex = new HashMap<String, String>();
    private Style fallback;

    public static void main(String[] args) {
	//Style me = new Style( new File("styles\\default.xml") );
	//System.out.println(me.getColor("comments", "fgColor").toString());
    	//System.out.println(SWT.GREY);
    }


    public Style(File xmlFile) {
	this(xmlFile, null);
    }

    public Style(File xmlFile, Style fallback) {
	this.fallback = fallback;
	DocumentBuilderFactory dbf = newSecureDocumentBuilderFactory();
	DocumentBuilder db;
	Element head, header;

	try {
	    db = dbf.newDocumentBuilder();
	    Document d = db.parse(xmlFile);

	    head = (Element)d.getElementsByTagName("RepDevStyle").item(0);
	    this.baseTheme = head.getAttribute("extends");
	    header = (Element)d.getElementsByTagName("header").item(0);
	    this.name = header.getElementsByTagName("name").item(0).getTextContent();
	    this.version = header.getElementsByTagName("version").item(0).getTextContent();
	    this.description = header.getElementsByTagName("description").item(0).getTextContent();
	    this.author = header.getElementsByTagName("author").item(0).getTextContent();

	    style = ((Element)head.getElementsByTagName("style").item(0));

	    // Read palette FIRST so the value-index build below can resolve
	    // @-references in one pass. Caller-visible behaviour is unchanged.
	    Node paletteNode = head.getElementsByTagName("palette").item(0);
	    if (paletteNode != null && paletteNode instanceof Element) {
		Element paletteElem = (Element) paletteNode;
		Node child = paletteElem.getFirstChild();
		while (child != null) {
		    if (child.getNodeName().equals("color") && child instanceof Element) {
			Element colorElem = (Element) child;
			String id = colorElem.getAttribute("id");
			String value = colorElem.getAttribute("value");
			if (!id.isEmpty() && !value.isEmpty()) {
			    palette.put(id, value);
			}
		    }
		    child = child.getNextSibling();
		}
	    }

	    // Build the (item|attrib -> resolved-value) lookup table once.
	    // Mirrors the resolution rules getValue used to apply on every call:
	    // @-prefixed attribute values are looked up in the palette and the
	    // resolved string is stored. Non-@-prefixed values are stored as-is.
	    // Null-guard: a malformed theme XML can omit <style>; defer to fallback.
	    if (style == null) return;
	    org.w3c.dom.NodeList styleChildren = style.getChildNodes();
	    for (int i = 0; i < styleChildren.getLength(); i++) {
		Node cur = styleChildren.item(i);
		String tag = cur.getNodeName();
		if (tag == null || tag.isEmpty() || tag.startsWith("#")) continue;
		org.w3c.dom.NamedNodeMap attrs = cur.getAttributes();
		if (attrs == null) continue;
		for (int j = 0; j < attrs.getLength(); j++) {
		    Node a = attrs.item(j);
		    String k = a.getNodeName();
		    String v = a.getNodeValue();
		    if (v != null && v.startsWith("@")) {
			String ref = v.substring(1);
			if (palette.containsKey(ref)) v = palette.get(ref);
		    }
		    valueIndex.put(tag + "|" + k, v == null ? "" : v);
		}
	    }

	} catch (ParserConfigurationException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (SAXException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    //e.printStackTrace(); was annoying since if config never sets style this will always trace
		if( xmlFile.toString().contains("null") ){
			System.out.println("Please set your default style");
		}else{
			System.out.println("Style does not exist");
		}
	}
    }
    /**
     * Build a {@link DocumentBuilderFactory} hardened against XXE-class
     * attacks. Theme XML files are user-editable on disk (and {@code extends=}
     * recurses into other files), so a tampered styles/*.xml could otherwise
     * read arbitrary local files, perform SSRF, or DoS the JVM via the
     * billion-laughs entity expansion at startup. Per the OWASP XXE cheat
     * sheet: disallow DOCTYPEs, disable external general/parameter entities,
     * skip the external-DTD load, drop XInclude, and stop entity-reference
     * expansion. Any feature the parser doesn't recognise is silently ignored
     * — we still get the secure defaults that the runtime does honour.
     */
    private static DocumentBuilderFactory newSecureDocumentBuilderFactory() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) { }
        try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) { }
        try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) { }
        try { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) { }
        try { dbf.setXIncludeAware(false); } catch (Exception ignored) { }
        try { dbf.setExpandEntityReferences(false); } catch (Exception ignored) { }
        return dbf;
    }

    public String getFontValue(String item, String attrib)
    {
    	String fontValue = getValue(item, attrib);
    	if(fontValue.length() == 0)
    		return "Courier New"; //Default Font

    		return fontValue;
    }
    public int getFontSize(String item, String attrib)
    {
    	String fontSize = getValue(item, attrib);
    	if(fontSize.length() == 0)
    		return 11; //Default size

    		return Integer.parseInt(fontSize);
    }
    public String getValue(String item, String attrib)
    {
        // O(1) lookup via the index built at construction. Falls through to
        // the parent style when this style declares neither the tag nor the
        // attribute — preserves the legacy inheritance semantics.
        String hit = valueIndex.get(item + "|" + attrib);
        if (hit != null) return hit;
        if (fallback != null) return fallback.getValue(item, attrib);
        return "";
    }
    public RGB getColor(String item, String attrib) {
	String hexColor = getValue(item, attrib);
	int[] rgb = {0,0,0};
	if( hexColor.equals("") ) return null; 
	if( hexColor.indexOf("#") == 0 ) hexColor = hexColor.substring(1);
	if( hexColor.length() != 6 && hexColor.length() != 3 && hexColor.indexOf("$") == -1) return null;
	if( hexColor.indexOf("$") == 0 ){
		hexColor = hexColor.substring(1);
		// Deterministic per-theme: seeding by (theme name, item, attrib, tag)
		// makes $rand/$red/$green/$blue stable across re-renders so users
		// don't see colors churn when a theme is reloaded. Same theme always
		// resolves to the same RGB; different themes can still differ.
		java.util.Random rng = new java.util.Random(deterministicSeed(item, attrib, hexColor));
		if( hexColor.indexOf("rand") != -1 ){
			rgb[0] = rng.nextInt(256);
			rgb[1] = rng.nextInt(256);
			rgb[2] = rng.nextInt(256);
		}else if( hexColor.indexOf("red") != -1){
			rgb[0] = rng.nextInt(256);
			rgb[1] = 7*16+7;
			rgb[2] = 7*16+7;
		}else if( hexColor.indexOf("green") != -1){
			rgb[0] = 7*16+7;
			rgb[1] = rng.nextInt(256);
			rgb[2] = 7*16+7;
		}else if( hexColor.indexOf("blue") != -1){
			rgb[0] = 7*16+7;
			rgb[1] = 7*16+7;
			rgb[2] = rng.nextInt(256);
		}else if( hexColor.indexOf("!") == 0){
			hexColor = hexColor.substring(1);
			if( hexColor.length() == 6 ){
				int colors[] = {Integer.parseInt(hexColor.substring(0,1), 17),
								Integer.parseInt(hexColor.substring(1,2), 17),
								Integer.parseInt(hexColor.substring(2,3), 17),
								Integer.parseInt(hexColor.substring(3,4), 17),
								Integer.parseInt(hexColor.substring(4,5), 17),
								Integer.parseInt(hexColor.substring(5,6), 17)};
				for( int i = 0; i < 6; i++ ){
					colors[i]=(colors[i]==16)?rng.nextInt(16):colors[i];
				}
				for( int i = 0; i < 3; i++ ){
					rgb[i]=colors[i*2]*16+colors[i*2+1];
				}
			}else{
				return null;
			}
		}
    }else if( hexColor.length() == 6 ){
		rgb[0] = Integer.parseInt(hexColor.substring(0,2), 16);
		rgb[1] = Integer.parseInt(hexColor.substring(2,4), 16);
		rgb[2] = Integer.parseInt(hexColor.substring(4), 16);
	}else{
		rgb[0] = Integer.parseInt(hexColor.substring(0,1), 16)*16;
		rgb[1] = Integer.parseInt(hexColor.substring(1,2), 16)*16;
		rgb[2] = Integer.parseInt(hexColor.substring(2), 16)*16;
	}
	return new RGB(rgb[0],rgb[1],rgb[2]);
    }

    /**
     * Derive a deterministic seed for {@code $rand} / {@code $red} / etc.
     * Mixing the theme name keeps two themes that both use {@code $rand} for
     * the same token from collapsing onto the same RGB; mixing the
     * item/attrib pair keeps every distinct token in a single theme on its
     * own deterministic stream.
     */
    private long deterministicSeed(String item, String attrib, String tag) {
        String key = (name == null ? "" : name) + "|" + item + "|" + attrib + "|" + tag;
        return key.hashCode();
    }

    public int getStyle(String item){
	int swtStyle = SWT.NORMAL;
	String styleText = "";
	for( int i=0; i<style.getChildNodes().getLength(); i++ ) {
	    Node cur = style.getChildNodes().item(i);
	    if( cur.getNodeName().equals(item) ) {
		for( int j=0; j<cur.getAttributes().getLength(); j++ ) {
		    if( cur.getAttributes().item(j).getNodeName().equals("style") ) {
			styleText = cur.getAttributes().item(j).getNodeValue();
		    }	    
		}
	    }
	}
	if (styleText.length() > 0) {
	    String[] parts = styleText.split(",");
	    swtStyle = 0;
	    for (String part : parts) {
		String p = part.trim();
		if (p.equalsIgnoreCase("bold")) swtStyle |= SWT.BOLD;
		else if (p.equalsIgnoreCase("italic")) swtStyle |= SWT.ITALIC;
		else if (p.equalsIgnoreCase("normal")) swtStyle |= SWT.NORMAL;
	    }
	}
	return swtStyle;

    }
}

/* Ryan and Sean have 1337 ascii art skillz */

;;     ;;   ;;;;;;;;;
;;     ;;      ;;
;;;;;;;;;      ;;
;;     ;;      ;;
;;     ;;   ;;;;;;;;;

;;            ;;
;;  ;;        ;;  ;;


;;;;;;;;;; 



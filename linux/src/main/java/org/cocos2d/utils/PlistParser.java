package org.cocos2d.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.cocos2d.nodes.CCDirector;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Original game plist handling with explicit offline SAX configuration for the JVM. */
public class PlistParser extends DefaultHandler {
    private static final int COLL_ARRAY = 2;
    private static final int COLL_DICT = 1;
    private static final int COLL_UNKNOWN = 0;
    private static final String EL_ARRAY = "array";
    private static final String EL_DATA = "data";
    private static final String EL_DATE = "date";
    private static final String EL_DICT = "dict";
    private static final String EL_FALSE = "false";
    private static final String EL_INTEGER = "integer";
    private static final String EL_KEY = "key";
    private static final String EL_REAL = "real";
    private static final String EL_STRING = "string";
    private static final String EL_TRUE = "true";
    private static final int TYPE_DATA = 4;
    private static final int TYPE_DATE = 5;
    private static final int TYPE_INTEGER = 3;
    private static final int TYPE_KEY = 1;
    private static final int TYPE_REAL = 6;
    private static final int TYPE_STRING = 2;
    private static final int TYPE_UNKNOWN = 0;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
    private Object currentCollection;
    private String currentKey;
    private Object rootDict;
    private int currentElement = 0;
    private int currentCollectionType = 0;
    private ArrayList<Object> depthStack = new ArrayList<>();
    private StringBuilder characterData = new StringBuilder();

    public static HashMap<String, Object> parse(String filename) {
        try {
            try (InputStream in = CCDirector.theApp.getAssets().open(filename)) {
                return parse(in);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static HashMap<String, Object> parse(InputStream in) {
        HashMap<String, Object> ret = (HashMap) parsePlist(in);
        return ret;
    }

    public static ArrayList<Object> parseArray(String filename) {
        try {
            try (InputStream in = CCDirector.theApp.getAssets().open(filename)) {
                return parseArray(in);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ArrayList<Object> parseArray(InputStream in) {
        ArrayList<Object> ret = (ArrayList) parsePlist(in);
        return ret;
    }

    private static Object parsePlist(InputStream in) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            SAXParser parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            PlistParser handler = new PlistParser();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192);
            parser.parse(new InputSource(reader), handler);
            return handler.rootDict;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        // Apple's bundled plist DOCTYPE is metadata, never a network dependency.
        return new InputSource(new StringReader(""));
    }

    private PlistParser() {
    }

    private void setupCurrentCollection(Object coll) {
        this.currentCollection = coll;
        if (coll == null) {
            this.currentCollectionType = 0;
        } else if (coll instanceof HashMap) {
            this.currentCollectionType = 1;
        } else {
            this.currentCollectionType = 2;
        }
    }

    private void depthUp(Object newcoll) {
        addToCollection(newcoll);
        this.depthStack.add(newcoll);
        setupCurrentCollection(newcoll);
    }

    private void depthDown() {
        int s = this.depthStack.size();
        if (s > 0) {
            this.depthStack.remove(s - 1);
            Object currCol = null;
            if (s > 1) {
                currCol = this.depthStack.get(s - 2);
            }
            setupCurrentCollection(currCol);
        }
    }

    private void addToCollection(Object obj) {
        switch (this.currentCollectionType) {
            case 0:
                this.rootDict = obj;
                break;
            case 1:
                ((HashMap) this.currentCollection).put(this.currentKey, obj);
                break;
            case 2:
                ((ArrayList) this.currentCollection).add(obj);
                break;
        }
    }

    @Override // org.xml.sax.helpers.DefaultHandler, org.xml.sax.ContentHandler
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (localName.equals(EL_KEY)) {
            this.currentElement = 1;
            return;
        }
        if (localName.equals(EL_STRING)) {
            this.currentElement = 2;
            return;
        }
        if (localName.equals(EL_INTEGER)) {
            this.currentElement = 3;
            return;
        }
        if (localName.equals(EL_REAL)) {
            this.currentElement = 6;
            return;
        }
        if (localName.equals("data")) {
            this.currentElement = 4;
            return;
        }
        if (localName.equals(EL_DATE)) {
            this.currentElement = 5;
            return;
        }
        if (localName.equals(EL_TRUE)) {
            addToCollection(true);
            return;
        }
        if (localName.equals(EL_FALSE)) {
            addToCollection(false);
        } else if (localName.equals(EL_DICT)) {
            depthUp(new HashMap());
        } else if (localName.equals(EL_ARRAY)) {
            depthUp(new ArrayList());
        }
    }

    @Override // org.xml.sax.helpers.DefaultHandler, org.xml.sax.ContentHandler
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (this.currentElement) {
            case 1:
                this.currentKey = this.characterData.toString().trim();
                break;
            case 2:
                addToCollection(this.characterData.toString().trim());
                break;
            case 3:
                addToCollection(Integer.valueOf(this.characterData.toString().trim()));
                break;
            case 4:
                try {
                    addToCollection(Base64.decode(this.characterData.toString().trim()));
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            case 5:
                try {
                    addToCollection(dateFormat.parse(this.characterData.toString().trim()));
                    break;
                } catch (ParseException e2) {
                    e2.printStackTrace();
                    break;
                }
            case 6:
                addToCollection(Double.valueOf(this.characterData.toString().trim()));
                break;
        }
        this.currentElement = 0;
        this.characterData.setLength(0);
        if (localName.equals(EL_DICT) || localName.equals(EL_ARRAY)) {
            depthDown();
        }
    }

    @Override // org.xml.sax.helpers.DefaultHandler, org.xml.sax.ContentHandler
    public void characters(char[] _chars, int _start, int _len) {
        this.characterData.append(_chars, _start, _len);
    }
}

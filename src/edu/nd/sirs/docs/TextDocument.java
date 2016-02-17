package edu.nd.sirs.docs;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.nd.sirs.parser.ITokenizer;
import edu.nd.sirs.parser.WhitespaceTextTokenizer;

/**
 * Document with only text to parse
 * 
 * @author tweninge
 *
 */
public class TextDocument extends Document {

	private static Logger logger = LoggerFactory.getLogger(TextDocument.class);

	/**
	 * Constructor from indexer
	 * 
	 * @param docId
	 *            document ID
	 * @param file
	 *            File to parse
	 */
	public TextDocument(Integer docId, String name) {
		super(docId, name);
	}

	/**
	 * Constructor from index reader
	 * 
	 * @param docId
	 *            document ID
	 * @param line
	 *            Text tokens to read
	 */
	public TextDocument(Integer docId, String line, Boolean b) {
		super(docId, line, b);
	}

	@Override
	public List<Token> parse(Integer docId, InputStream is) {
		Fields.getInstance().addField("body");
		List<Token> tokens = new ArrayList<Token>();
		ITokenizer tokenizer = new WhitespaceTextTokenizer();
		List<String> toks = tokenizer.tokenize(this.readFile(is));
		for(String t: toks){
			tokens.add(new Token(t, Fields.getInstance().getFieldId("body")));
		}
		
		numTokens.put(Fields.getInstance().getFieldId("body"), toks.size());

		return tokens;
	}

}

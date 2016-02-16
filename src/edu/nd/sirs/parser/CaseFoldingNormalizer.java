package edu.nd.sirs.parser;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Normalizes text by case folding
 * 
 * @author tweninge
 *
 */
public class CaseFoldingNormalizer implements INormalizer {

	private static Logger logger = LoggerFactory.getLogger(CaseFoldingNormalizer.class);

	public List<String> normalize(List<String> str) {
		logger.info("Case Folding Normalizer...");
		List<String> to_return = new ArrayList<String>(str.size());
		for(String s : str) {
			to_return.add(s.toLowerCase());
		}
		return to_return;
	}
}

package edu.nd.sirs.parser;

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
		//we need to perform case folding
		return str;
	}
}

package edu.nd.sirs.retrievalmodel;

import edu.nd.sirs.index.InvertedIndex;
import edu.nd.sirs.query.Query;
import edu.nd.sirs.query.ResultSet;

/**
 * Cosine Score Modifier that performs Cosine Distance with Document Length Normalization on all results.
 * 
 * @author tweninge
 *
 */
public class CosineScoreModifier implements ScoreModifier {

	/**
	 * Cosine score with TF-IDF
	 */
	public boolean modifyScores(InvertedIndex index, Query query,
			ResultSet resultSet) {
		short[] occurrences = resultSet.getOccurrences();
		float[] scores = resultSet.getScores();
		int size = resultSet.getResultSize();
		int start = 0;
		int end = size;
		int numOfModifiedDocumentScores = 0;
		short queryLengthMask = 0;
		
		for (int i = 0; i < query.getTerms().size(); i++) {
			queryLengthMask = (short) ((queryLengthMask << 1) + 1);
		}

		// modify the scores
		for (int i = start; i < end; i++) {
			if ((occurrences[i] & queryLengthMask) != queryLengthMask) {
				if (scores[i] > Float.NEGATIVE_INFINITY)
					numOfModifiedDocumentScores++;
				scores[i] = Float.NEGATIVE_INFINITY;
			}
		}
		if (numOfModifiedDocumentScores == 0) {
			return false;
		}
		resultSet.setResultSize(size - numOfModifiedDocumentScores);
		resultSet.setExactResultSize(resultSet.getExactResultSize()
				- numOfModifiedDocumentScores);
		return true;
	}

}

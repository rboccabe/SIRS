package edu.nd.sirs.retrievalmodel;

import edu.nd.sirs.index.InvertedIndex;
import edu.nd.sirs.index.DirectIndex;
import edu.nd.sirs.query.Query;
import edu.nd.sirs.query.ResultSet;
import edu.nd.sirs.docs.HTMLDocument;

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
		int end = occurrences.length;
		int numOfModifiedDocumentScores = 0;
		int docIDs[] = resultSet.getDocids();

		
		// modify the scores
		for (int i = start; i < end; i++) {
			numOfModifiedDocumentScores++;
			scores[i] /= DirectIndex.getInstance().getDoc(docIDs[i], HTMLDocument.class).getNumTokens();
		}

		int numUnmodifiedDocumentScores = scores.length - numOfModifiedDocumentScores;
		resultSet.setResultSize(size - numUnmodifiedDocumentScores);
		resultSet.setExactResultSize(resultSet.getExactResultSize()
				- numUnmodifiedDocumentScores);
		resultSet.sort(numOfModifiedDocumentScores);
		return true;
	}

}

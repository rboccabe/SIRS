package edu.nd.sirs.retrievalmodel;

import edu.nd.sirs.index.DirectIndex;
import edu.nd.sirs.index.Posting;
import java.lang.Math;

/**
 * TF-IDF Retrieval Model
 * 
 * @author tweninge
 *
 */
public class CosineRM implements IRetrievalModel {

	private DirectIndex DI = DirectIndex.getInstance();
	
	private float numDocs = (float) DI.getNumDocs();
	/**
	 * Perform TF-IDF weighting for cosine similarity
	 */
	public float score(Posting q, long df) {
		//                                   Makes use of Logarithm Base Change Rule
		//                    TF           IDF = ( ln(N / dfi)       /    ln(2))
		return (float) (q.getFrequency() * Math.log(numDocs/(float) df) / Math.log(2.0f));
	}

}

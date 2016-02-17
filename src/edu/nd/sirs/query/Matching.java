package edu.nd.sirs.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.nd.sirs.docs.Field;
import edu.nd.sirs.docs.Fields;
import edu.nd.sirs.docs.TextDocument;
import edu.nd.sirs.index.DirectIndex;
import edu.nd.sirs.index.InvertedIndex;
import edu.nd.sirs.index.Lexicon;
import edu.nd.sirs.index.Posting;
import edu.nd.sirs.index.PostingList;
import edu.nd.sirs.retrievalmodel.BooleanRM;
import edu.nd.sirs.retrievalmodel.BooleanScoreModifier;
import edu.nd.sirs.retrievalmodel.IRetrievalModel;
import edu.nd.sirs.retrievalmodel.ScoreModifier;

/**
 * Perform basic matching to answer queries
 * 
 * @author tweninge
 *
 */
public class Matching {

	private static final int RETRIEVED_SET_SIZE = 200;

	private HashMap<String, Integer> queryTermsToMatchList = null;
	private List<ScoreModifier> scoreModifiers = null;

	private int numRetrievedDocs;
	private InvertedIndex index;
	private ResultSet resultSet;
	private IRetrievalModel scorer;

	/**
	 * Simple constructor.
	 * 
	 * @param retrievalModel
	 *            Retrieval model object to use to score documents.
	 */
	public Matching(IRetrievalModel retrievalModel) {
		scoreModifiers = new ArrayList<ScoreModifier>();
		scorer = retrievalModel;
		index = InvertedIndex.getInstance();		
	}

	/**
	 * Accumulates scores for documents that match query terms.
	 * 
	 * @param queryTerms
	 *            Query with terms
	 * @return ResultSet of ranked documents
	 */
	public ResultSet match(Query queryTerms) {
		init(queryTerms);

		numRetrievedDocs = 0;

		final HashMap<Field, ResultSet> results = new HashMap<Field, ResultSet>();

		final int queryLength = queryTermsToMatchList.size();

		// The posting list iterator array (one per term) and initialization
		List<PostingList> postingListArray = new ArrayList<PostingList>(
				queryLength);
		for (String term : queryTermsToMatchList.keySet()) {
			int termId = queryTermsToMatchList.get(term);

			postingListArray.add(index.getPostings(termId));

			// long docid = postingListArray(i).getId();
			// postingHeap.enqueue((docid << 32) + i);
		}

		for (Field f : Fields.getInstance().getFields()) {

			final HashMap<Integer, Hit> accumulators = new HashMap<Integer, Hit>();

			boolean targetResultSetSizeReached = false;
			PostingList currentPostingList = null;

			// while not end of all posting lists
			for (int currentPostingListIndex = 0; currentPostingListIndex < postingListArray
					.size(); currentPostingListIndex++) {

				currentPostingList = postingListArray
						.get(currentPostingListIndex);
				for (int currentPosting = 0; currentPosting < currentPostingList
						.size(f); currentPosting++) {

					int currentDocId = postingListArray
							.get(currentPostingListIndex).getPostings(f)
							.get(currentPosting).getDocid();

					// We create a new hit for each new doc id considered
					Hit currentCandidate = null;
					if (accumulators.containsKey(currentDocId)) {
						currentCandidate = accumulators.get(currentDocId);
					} else {
						currentCandidate = new Hit(currentDocId);
					}
					accumulators.put(currentDocId, currentCandidate);

					assignScore(currentPostingListIndex, scorer,
							currentCandidate, currentPostingList.getPostings(f)
									.get(currentPosting),
							currentPostingList.getDocumentFrequency(), f);
				}

				if ((!targetResultSetSizeReached)) {
					if (accumulators.size() >= RETRIEVED_SET_SIZE) {
						targetResultSetSizeReached = true;
					}
				}
			}

			resultSet = new ResultSet(accumulators.values());
			numRetrievedDocs = resultSet.getScores().length;
			finalize(queryTerms, f);
			results.put(f, resultSet);
		}

		Map<Integer, Hit> finalscores = new TreeMap<Integer, Hit>();
		for (Field f : Fields.getInstance().getFields()) {
			for (int i = 0; i < results.get(f).getDocids().length; i++) {

				if (!finalscores.containsKey(results.get(f).getDocids()[i])) {
					finalscores.put(results.get(f).getDocids()[i], new Hit(
							results.get(f).getDocids()[i]));
				}
				float wghtdScore = results.get(f).getScores()[i]
						* Fields.getInstance().getWeight(f);
				finalscores.get(results.get(f).getDocids()[i]).updateScore(
						wghtdScore);
				finalscores.get(results.get(f).getDocids()[i])
						.updateOccurrence((short) 1);
			}
		}

		ResultSet rs = new ResultSet(finalscores.values());
		numRetrievedDocs = finalscores.values().size();

		int setSize = Math.min(RETRIEVED_SET_SIZE, numRetrievedDocs);
		if (setSize == 0)
			setSize = numRetrievedDocs;

		rs.setExactResultSize(numRetrievedDocs);
		rs.setResultSize(setSize);
		rs.sort(setSize);

		return rs;

	}

	public int getNumResults() {
		return numRetrievedDocs;
	}

	/**
	 * Adds a score modifier to the finalizer function
	 * 
	 * @param sm
	 */
	public void addScoreModifier(ScoreModifier sm) {
		scoreModifiers.add(sm);
	}

	/**
	 * Turns Query of terms into a list of termIds by using the Lexicon Index
	 * 
	 * @param queryTerms
	 */
	private void init(Query queryTerms) {
		List<String> queryTermStrings = queryTerms.getTerms();
		queryTermsToMatchList = new HashMap<String, Integer>(
				queryTermStrings.size());
		for (String queryTerm : queryTermStrings) {
			Integer t = Lexicon.getInstance().getTermId(queryTerm);
			if (t != -1) {
				queryTermsToMatchList.put(queryTerm, t);
			} else {
				System.err.println("Term not found");
			}
		}
	}

	/**
	 * Runs all of the score finalizers
	 * 
	 * @param queryTerm
	 */
	private void finalize(Query queryTerms, Field f) {
		int setSize = Math.min(RETRIEVED_SET_SIZE, numRetrievedDocs);
		if (setSize == 0)
			setSize = numRetrievedDocs;

		resultSet.setExactResultSize(numRetrievedDocs);
		resultSet.setResultSize(setSize);
		resultSet.sort(setSize);

		for (int t = 0; t < scoreModifiers.size(); t++) {
			if (scoreModifiers.get(t).modifyScores(index, queryTerms,
					resultSet, f))
				resultSet.sort(resultSet.getResultSize());
		}
	}

	/**
	 * Interface to the retrieval model.
	 * 
	 * @param i
	 *            posting list position
	 * @param wModels
	 *            retrieval model
	 * @param h
	 *            hit (aka result)
	 * @param posting
	 *            posting matching term form query
	 */
	private void assignScore(int i, final IRetrievalModel wModels, Hit h,
			final Posting posting, final long df, final Field field) {
		h.updateScore(wModels.score(posting, df));
		h.updateOccurrence((i < 16) ? (short) (1 << i) : 0);
	}

	/**
	 * Simple testing main method
	 * 
	 * @param args
	 *            none needed
	 */
	public static void main(String[] args) {
		Matching m = new Matching(new BooleanRM());
		m.addScoreModifier(new BooleanScoreModifier());
		ResultSet rs = m.match(new Query("Notre Dame"));
		for (int i : rs.getDocids()) {
			DirectIndex.getInstance().getDoc(i, TextDocument.class);
		}
		System.out.println(rs);
	}
}

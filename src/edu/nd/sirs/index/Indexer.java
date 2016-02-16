package edu.nd.sirs.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.nd.sirs.docs.Document;
import edu.nd.sirs.docs.HTMLDocument;
import edu.nd.sirs.docs.TextDocument;

/**
 * Creates direct and inverted indexes for the documents stored in the folder.
 * 
 * @author tweninge
 *
 */
public class Indexer {

	private static Logger logger = LoggerFactory.getLogger(Indexer.class);

	private static final String DOCIDX = "/home/ryan/data/doc_idx.txt";
	private static final String DOCIDXOFFSET = "/home/ryan/data/doc_idx_offset.txt";
	private static final String LEXICON = "/home/ryan/data/lex.txt";
	private static final String RUNSPREFIX = "/home/ryan/data/runs/run";
	private static final String IDX = "/home/ryan/data/idx.txt";
	private static final String IDXTERMOFFSET = "/home/ryan/data/idx_term_offset.txt";

	private static final Integer RUN_SIZE = 1000000;
	private static final Boolean COMPRESS = false;

	private int wordId;
	private int docId;
	private List<DocumentTerm> run;
	private int runNumber;

	private TreeMap<String, Integer> voc;

	/**
	 * Indexer Constructor
	 */
	public Indexer() {
		wordId = 0;
		docId = 0;
		runNumber = 0;
		voc = new TreeMap<String, Integer>();
	}

	/**
	 * Create direct and inverted indices for each file in the list of files.
	 * 
	 * @param filesToIndex
	 *            files to index
	 */
	private void indexDirectory(File[] filesToIndex) {
		docId = 0;

		PrintWriter docWriter;
		PrintWriter docWriterOffset;
		try {
			docWriter = new PrintWriter(DOCIDX);
			docWriterOffset = new PrintWriter(DOCIDXOFFSET);

			// start the first run
			logger.info("Starting the first indexer run.");
			run = new ArrayList<DocumentTerm>();
			int written = 0;
			for (File file : filesToIndex) {
				logger.info("Indexing document " + file.getName());
				Document doc = new HTMLDocument(docId, file);
				List<String> tokens = doc.parse(docId, file);
				index(tokens);
				docWriterOffset.write(written + "\n");

				// Writing to Direct Index
				String idxable = doc.writeToIndex();
				docWriter.write(idxable);
				written += idxable.length();
				docId++;
			}
			docWriter.close();
			docWriterOffset.close();

			// If there is something yet in the last run, sort it and store
			if (run.size() > 0) {
				logger.info("Writing file run to disk.");
				storeRun();
			}

			logger.info("Indexing runs complete.");
		} catch (FileNotFoundException e) {
			logger.error("Cannot find direct index file.", e);
		}

		try {
			mergeRuns();
		} catch (FileNotFoundException e) {
			logger.error("Cannot find inverted index file.", e);
		}

		// Output the vocabulary
		try {
			outputLexicon();
		} catch (FileNotFoundException e) {
			logger.error("Cannot find lexicon file.", e);
		}
		logger.info("Indexing complete.");
	}

	private void outputLexicon() throws FileNotFoundException {
		logger.info("Writing lexicon to disk");
		PrintWriter lexFile = new PrintWriter(LEXICON);
		for (Entry<String, Integer> x : voc.entrySet()) {
			lexFile.println(x.getKey() + "\t" + x.getValue());
		}
		lexFile.close();
		logger.info("Lexicon writing finished");
	}

	/**
	 * Merge the runs together to make a single inverted index
	 * 
	 * @throws FileNotFoundException
	 */
	private void mergeRuns() throws FileNotFoundException {

		// Create the heap
		PriorityQueue<MergeDocumentTerms> mergeHeap = new PriorityQueue<MergeDocumentTerms>();
		List<RunFile> rfv = new ArrayList<RunFile>();
		String filename;
		DocumentTerm ocurr;
		MergeDocumentTerms ro;
		for (int i = 0; i < runNumber; ++i) {
			filename = RUNSPREFIX + i;
			rfv.add(new RunFile(new File(filename), RUN_SIZE / runNumber));
			// get the first element and put it in the heap
			ocurr = rfv.get(i).getRecord();
			if (ocurr == null) {
				logger.error("Error: Record was not found.");
				return;
			}
			ro = new MergeDocumentTerms(ocurr, i);
			mergeHeap.add(ro);
		}
		long currentTerm = 0l;
		long currentTermOffset = 0l;
		PrintWriter outFile = new PrintWriter(IDX);
		PrintWriter tosFile = new PrintWriter(IDXTERMOFFSET);
		String wid = wordId + "\n";
		tosFile.print(wid);

		MergeDocumentTerms first;
		logger.info("Merging run files...");
		
		int df = 0;
		StringBuffer posting = new StringBuffer();
		
		while (!mergeHeap.isEmpty()) {
			first = mergeHeap.poll();

			// Get a new posting from the same run and
			// put it in the heap, if possible
			ocurr = rfv.get(first.run).getRecord();
			if (ocurr != null) {
				ro = new MergeDocumentTerms(ocurr, first.run);
				mergeHeap.add(ro);
			}
			// Saving to the file
			if (first.getTermId() > currentTerm) {
				tosFile.println(currentTermOffset);
				String p = currentTerm + ":" + df + "\t" + posting + "\n";
				outFile.print(p);
				currentTermOffset += p.getBytes().length;
				currentTerm = first.getTermId();
				posting = new StringBuffer();
				df=0;
			} else if (first.getTermId() < currentTerm) {
				logger.error("Term ids messed up, something went wrong with the sorting");
			}
			if (COMPRESS) {
				// not yet
			} else {
				df++;
				posting.append("(" + first.getDocId() + ","
						+ first.getFrequency() + ");");
			}
		}
		outFile.close();
		tosFile.close();
		logger.info("Index merging finished");
	}

	/**
	 * Creates a local vocabulary and indexes terms one-by-one
	 * 
	 * @param tokens
	 *            list of tokens for indexing
	 */
	private void index(List<String> tokens) {
		HashMap<Integer, DocumentTerm> lVoc = new HashMap<Integer, DocumentTerm>();
		for (String token : tokens) {
			if(token.equals("weninger")){
				System.out.println();
			}
			index(token, docId, lVoc);
		}

		for (DocumentTerm p : lVoc.values()) {
			if (run.size() < RUN_SIZE) {
				run.add(p);
			} else {
				logger.info("Current indexing run full, storing to disk.");
				storeRun();
				run.add(p);
			}
		}
	}

	/**
	 * Store the current run on disk.
	 */
	private void storeRun() {
		// creating the output file
		try {
			long runId = getRunNumber();
			File outName = new File(RUNSPREFIX + runId);
			if (!outName.getParentFile().exists()) {
				logger.info("Creating run directory");
				outName.getParentFile().mkdir();
			}
			if (outName.exists()) {
				logger.warn("Run directory already exists - deleting");
				outName.delete();
			}
			PrintWriter outFile = new PrintWriter(outName);

			logger.info("Sorting the current run");
			Collections.sort(run);

			// Storing it
			for (DocumentTerm p : run) {
				outFile.println(p.getDocId() + "\t" + p.getTermId() + "\t"
						+ p.getFrequency());
			}
			outFile.close();
		} catch (FileNotFoundException e) {
			logger.error("Cannot find run file within " + RUNSPREFIX, e);
		}
		run.clear();
	}

	/**
	 * Does needed math to return appropriate run number
	 * 
	 * @return current run number
	 */
	private long getRunNumber() {
		++runNumber;
		return runNumber - 1;
	}

	private int getNewId() {
		++wordId;
		return wordId - 1;
	}

	/**
	 * Creates a DocumentTerm pair from token and docid and adds it to the local
	 * vocabulary
	 * 
	 * @param token
	 *            Token to index
	 * @param docId
	 *            Document Id containing Token
	 * @param lVoc
	 *            local dictionary of Tokens->DocumentTerm
	 */
	private void index(String token, int docId, HashMap<Integer, DocumentTerm> lVoc) {
		int termId;
		if (!voc.containsKey(token)) {
			termId = getNewId();
			voc.put(token, termId);
		} else {
			termId = voc.get(token);
		}

		if (!lVoc.containsKey(termId)) {
			DocumentTerm p = new DocumentTerm(termId, docId, 1);
			lVoc.put(termId, p);
		} else {
			DocumentTerm p = lVoc.get(termId);
			p.incrementFrequency();
			// do we need this?
			lVoc.put(termId, p);
		}
	}

	/**
	 * Get files, and only files, from within the specified directory.
	 * 
	 * @param dir
	 *            directory in which to look for files
	 * @return array of files found in dir.
	 */
	private File[] getFiles(File dir) {
		if (!dir.isDirectory()) {
			logger.error(dir + " not a directory of files.");
			System.exit(1);
		}
		return dir.listFiles(new FilenameFilter() {
			/**
			 * Only accept files within the directory... do not recur into
			 * subdirectories.
			 */
			public boolean accept(File dir, String name) {
				return new File(dir, name).isFile();
			}

		});
	}

	private static final String CRL = "/home/ryan/data/crawl";

	public static void main(String[] args) {
		File crawl = null;
		if (args.length == 1) {
			logger.info("Using user provided parameters");
			try {
				crawl = new File(args[0]);
			} catch (Exception e) {
				printUsage(e);
			}
		} else {
			logger.info("User did not provide 1 input argument; reverting to defaults...");
			crawl = new File(CRL);
		}

		Indexer idxr = new Indexer();
		File[] filesToIndex = idxr.getFiles(crawl);
		idxr.indexDirectory(filesToIndex);
	}

	private static void printUsage(Exception e) {
		logger.error("Error parsing user provided parameters: "
				+ "Indexer <crawlerDataFolder>", e);
	}

}

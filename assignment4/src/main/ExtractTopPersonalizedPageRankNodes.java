import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import edu.umd.cloud9.util.TopNScoredObjects;
import edu.umd.cloud9.util.pair.PairOfObjectFloat;


public class Extract implements Tool {
	private static final Logger LOG = Logger.getLogger(Extract.class);

	
	private static final String INPUT = "input";
	private static final String TOP = "top";
	private static final String SOURCES = "sources";
	
	@SuppressWarnings("static-access")
	@Override
	public int run(String[] args) throws Exception {
		// TODO Auto-generated method stub
		
		Options options = new Options();

		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("input path").create(INPUT));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("top n").create(TOP));
		options.addOption(OptionBuilder.withArgName("sources").hasArg()
				.withDescription("source nodes").create(SOURCES));

		CommandLine cmdline;
		CommandLineParser parser = new GnuParser();

		try {
			cmdline = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println("Error parsing command line: " + exp.getMessage());
			return -1;
		}

		if (		!cmdline.hasOption(INPUT) 
				||  !cmdline.hasOption(TOP)
				||  !cmdline.hasOption(SOURCES)) {
			System.out.println("args: " + Arrays.toString(args));
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp(this.getClass().getName(), options);
			ToolRunner.printGenericCommandUsage(System.out);
			return -1;
		}

		String inputPath = cmdline.getOptionValue(INPUT);
		int n = Integer.parseInt(cmdline.getOptionValue(TOP));
		String sourcesString = cmdline.getOptionValue(SOURCES);

		LOG.info("Tool name: " + Extract.class.getSimpleName());
		LOG.info(" - input: " + inputPath);
		LOG.info(" - top: " + n);
		LOG.info(" - sources: " + sourcesString);
		
		getTopNodes(inputPath, sourcesString, n);
		
		return 0;
	}

	private void getTopNodes(String inputPathString, String sourcesString, int numResults) throws IOException, InstantiationException, IllegalAccessException {

		Configuration conf = new Configuration();
		Path inputPath = new Path(inputPathString + "/part-m-00000");
		SequenceFile.Reader reader = new SequenceFile.Reader(FileSystem.get(conf), inputPath, conf);
		
		IntWritable key = (IntWritable) reader.getKeyClass().newInstance();
		PageRankNodeExtended value = (PageRankNodeExtended) reader.getValueClass().newInstance();
		
		ArrayList<TopNScoredObjects<Integer>> queueList = new ArrayList<TopNScoredObjects<Integer>>();
		String[] sources = sourcesString.split(",");
		for(int i = 0; i < sources.length; i++){
			queueList.add(i, new TopNScoredObjects<Integer>(numResults));
		}
		
		
		while(reader.next(key, value)){
			
			for(int i = 0; i < sources.length; i++){
				queueList.get(i).add(key.get(), value.getPageRank(i));
			}
			
		}
		reader.close();
		
		
		for(int i = 0; i < sources.length; i++){
			
			System.out.println("Source: " + sources[i]);
			//Print out top k
			TopNScoredObjects<Integer> list = queueList.get(i);
			for(PairOfObjectFloat<Integer> pair : list.extractAll()){
				
				int nodeid = ((Integer) pair.getLeftElement());
				float pagerank = (float) Math.exp(pair.getRightElement());
				System.out.println(String.format("%.5f %d", pagerank, nodeid));
			}
			
			System.out.println("");
			
		}
		
		
//		String[] sources = sourcesString.split(",");
//		
//		// Write to a file the amount of PageRank mass we've seen in this reducer.
//		Configuration conf = new Configuration();
//		FileSystem fs = FileSystem.get(conf);
//		FSDataInputStream in = fs.open(new Path(inputPath));
//		FSDataOutputStream out = fs.create(new Path(outputPath), false);
//		
//					
//		
//		for(String source : sources){
//			TopNScoredObjects<Integer> queue = new TopNScoredObjects<Integer>(numResults);
//			
//			
//			
//			//Write out
//			// Source: <source>
//			//	each of the top numResults items
//			
//		}
		
		
//		out.close();
		
		
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		ToolRunner.run(new Extract(), args);
	}

	@Override
	public Configuration getConf() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setConf(Configuration arg0) {
		// TODO Auto-generated method stub
		
	}



}

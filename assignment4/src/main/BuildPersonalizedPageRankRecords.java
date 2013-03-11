/*
 * Cloud9: A Hadoop toolkit for working with big data
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import edu.umd.cloud9.io.array.ArrayListOfFloatsWritable;
import edu.umd.cloud9.io.array.ArrayListOfIntsWritable;

/**
 * <p>
 * Driver program that takes a plain-text encoding of a directed graph and builds corresponding
 * Hadoop structures for representing the graph.
 * </p>
 *
 * @author Jimmy Lin
 * @author Michael Schatz
 */
public class BuildPersonalizedPageRankRecords extends Configured implements Tool {
	private static final Logger LOG = Logger.getLogger(BuildPersonalizedPageRankRecords.class);

	private static final String NODE_CNT_FIELD = "node.cnt";

	private static class MyMapper extends Mapper<LongWritable, Text, IntWritable, PageRankNodeExtended> {
		private static final IntWritable nid = new IntWritable();
		private static final PageRankNodeExtended node = new PageRankNodeExtended();
		private static final HashMap<Integer, Integer> sourceIds = new HashMap<Integer, Integer>();

		@Override
		public void setup(Mapper<LongWritable, Text, IntWritable, PageRankNodeExtended>.Context context) {

			String[] sourceList = context.getConfiguration().getStrings("sources");
			if(sourceList.length == 0){
				throw new RuntimeException("Source list cannot be empty!");
			}

			for(int i = 0; i < sourceList.length; i++){
				sourceIds.put(Integer.parseInt(sourceList[i]), i);
			}

			int n = context.getConfiguration().getInt(NODE_CNT_FIELD, 0);
			if (n == 0) {
				throw new RuntimeException(NODE_CNT_FIELD + " cannot be 0!");
			}
			node.setType(PageRankNodeExtended.Type.Complete);
		}

		@Override
		public void map(LongWritable key, Text t, Context context) throws IOException,
		InterruptedException {
			String[] arr = t.toString().trim().split("\\s+");

			nid.set(Integer.parseInt(arr[0]));
			if (arr.length == 1) {
				node.setNodeId(Integer.parseInt(arr[0]));
				node.setAdjacencyList(new ArrayListOfIntsWritable());

			} else {
				node.setNodeId(Integer.parseInt(arr[0]));

				int[] neighbors = new int[arr.length - 1];
				for (int i = 1; i < arr.length; i++) {
					neighbors[i - 1] = Integer.parseInt(arr[i]);
				}

				node.setAdjacencyList(new ArrayListOfIntsWritable(neighbors));
			}

			int nodeId = nid.get();
			float[] pageranks = new float[sourceIds.size()];
			for(int sourceId : sourceIds.keySet()){
				int position = sourceIds.get(sourceId);

				if(node.getNodeId() == sourceId){
					LOG.info("DEBUG: Source node found: adding default pagerank of 0.0 to " + nodeId);
					pageranks[position] = (float) StrictMath.log(1.0);
				} else {
					LOG.info("DEBUG: NodeId: " + nodeId + " is not a source. Getting pagerank: -Infinity");
					pageranks[position] = (float) StrictMath.log(0.0);
				}

			}

			node.setPageRankArray(new ArrayListOfFloatsWritable(pageranks));

			context.getCounter("graph", "numNodes").increment(1);
			context.getCounter("graph", "numEdges").increment(arr.length - 1);

			if (arr.length > 1) {
				context.getCounter("graph", "numActiveNodes").increment(1);
			}

			context.write(nid, node);
		}
	}

	public BuildPersonalizedPageRankRecords() {}

	private static final String INPUT = "input";
	private static final String OUTPUT = "output";
	private static final String NUM_NODES = "numNodes";
	private static final String SOURCES = "sources";

	/**
	 * Runs this tool.
	 */
	@SuppressWarnings({ "static-access" })
	public int run(String[] args) throws Exception {
		Options options = new Options();

		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("input path").create(INPUT));
		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("output path").create(OUTPUT));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("number of nodes").create(NUM_NODES));
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

		if (!cmdline.hasOption(INPUT) || !cmdline.hasOption(OUTPUT) 
				|| !cmdline.hasOption(NUM_NODES)
				|| !cmdline.hasOption(SOURCES)) {
			System.out.println("args: " + Arrays.toString(args));
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp(this.getClass().getName(), options);
			ToolRunner.printGenericCommandUsage(System.out);
			return -1;
		}

		String inputPath = cmdline.getOptionValue(INPUT);
		String outputPath = cmdline.getOptionValue(OUTPUT);
		int n = Integer.parseInt(cmdline.getOptionValue(NUM_NODES));
		String sources = cmdline.getOptionValue(SOURCES);

		LOG.info("Tool name: " + BuildPersonalizedPageRankRecords.class.getSimpleName());
		LOG.info(" - inputDir: " + inputPath);
		LOG.info(" - outputDir: " + outputPath);
		LOG.info(" - numNodes: " + n);
		LOG.info(" - sources: " + sources);


		Configuration conf = getConf();
		conf.setInt(NODE_CNT_FIELD, n);
		conf.setInt("mapred.min.split.size", 1024 * 1024 * 1024);
		conf.setStrings("sources", sources);

		Job job = Job.getInstance(conf);
		job.setJobName(BuildPersonalizedPageRankRecords.class.getSimpleName() + ":" + inputPath);
		job.setJarByClass(BuildPersonalizedPageRankRecords.class);

		job.setNumReduceTasks(0);

		FileInputFormat.addInputPath(job, new Path(inputPath));
		FileOutputFormat.setOutputPath(job, new Path(outputPath));

		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);

		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(PageRankNodeExtended.class);

		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(PageRankNodeExtended.class);

		job.setMapperClass(MyMapper.class);

		// Delete the output directory if it exists already.
		FileSystem.get(conf).delete(new Path(outputPath), true);

		job.waitForCompletion(true);

		return 0;
	}

	/**
	 * Dispatches command-line arguments to the tool via the {@code ToolRunner}.
	 */
	public static void main(String[] args) throws Exception {
		ToolRunner.run(new BuildPersonalizedPageRankRecords(), args);
	}
}

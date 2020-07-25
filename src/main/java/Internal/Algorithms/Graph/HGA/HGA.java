package Internal.Algorithms.Graph.HGA;


import Internal.Algorithms.Graph.Hungarian;
import Internal.Algorithms.Graph.NBM;
import Internal.Algorithms.Graph.Network.Edge;
import Internal.Algorithms.Graph.Utils.AdjList.UndirectedGraph;
import Internal.Algorithms.Graph.Utils.SimMat;
import Internal.Algorithms.IO.AbstractFileWriter;
import Internal.Algorithms.Tools.GPUKernelForHGA;
import com.aparapi.Range;
import com.aparapi.device.Device;
import org.apache.commons.io.FileUtils;
import org.jblas.DoubleMatrix;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.alg.util.Triple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

/**
 * Refer to An Adaptive Hybrid Algorithm for Global Network Internal.Algorithms.Alignment
 * Article in IEEE/ACM Transactions on Computational Biology and Bioinformatics · January 2015
 * DOI: 10.1109/TCBB.2015.2465957
 *
 * @author: Haotian Bai
 * Shanghai University, department of computer science
 */

public class HGA {
    private static final int LimitOfIndexGraph = 60;

    protected static SimMat simMat;
    protected static UndirectedGraph udG1;
    protected static UndirectedGraph udG2;
    // parameters
    private static boolean forcedMappingForSame;
    private static double hAccount;
    protected static double bioFactor;
    private static double edgeScore = 1.;
    private static int h = 5;
    //---------------mapping result(best mapping)-------------
    public static HashMap<String, String> mappingResult;
    public static double PE_res;
    public static double ES_res;
    public static double PS_res;
    public static double EC_res;
    public static double score_res;
    public static DoubleMatrix matrix_res;
    //---------------mapping for iteration---------
    public static HashMap<String, String> mapping;
    public static double PE;
    public static double ES;
    public static double PS;
    public static double EC;
    public static double score;
    private static SimMat originalMat;
    private Stack<DoubleMatrix> stackMat;
    private Stack<Double> stackScore;
    private static double sumPreSimMat;
    //----------limit-----
    private final int splitLimit = 20;
    private int iterCount = 0;
    private int iterMax = 1000;
    //--------------debug---------------
    public static String debugOutputPath = "src\\test\\java\\resources\\Jupiter\\data\\";
    //--------------Logging-------------
    public static Logger logger;
    private static AbstractFileWriter writer;
    public static boolean debugOut = true;
    public static boolean log = true;
    public static boolean GPU = false;
    private double tolerance;
    public int iter_res;
    public static Vector<Pair<Edge, Edge>> mappingEdges;


    /**
     * Step 1:
     * using homologous coefficients of proteins
     * computed by alignment algorithms for PINs
     *
     * @param undG1                adjacent list of graph1
     * @param udG2                 adjacent list of graph2
     * @param simMat               similarity matrix, headNode->graph1, listNodes -> graph2
     * @param bioFactor            sequence similarity account compared with topological effect
     * @param forcedMappingForSame whether force mapping
     * @param hAccount             hungarian matrix account
     */
    public HGA(SimMat simMat, UndirectedGraph undG1, UndirectedGraph udG2, double bioFactor, boolean forcedMappingForSame, double hAccount, double tolerance) throws IOException {

        this.udG1 = undG1;
        this.udG2 = udG2;
        originalMat = (SimMat) simMat.dup();
        this.simMat = simMat;
        this.forcedMappingForSame = forcedMappingForSame;
        this.tolerance = tolerance;
        // set up preferences
        setBioFactor(bioFactor);
        sethAccount(hAccount);
        // if noneZerosMap isn't updated, sum() should not be used
//        simMat.updateNonZerosForRow = true;
        // set up logging
        if (log) setupLogger();
    }

    /**
     * HGA to initialize the mapping between two graph by HA,
     * Notice before using this method, make sure matrix is updated, because Hungarian use matrix index directly
     *
     * @return the mapping result
     */
    protected HashMap<String, String> getMappingFromHA(SimMat simMat) {
        logInfo("Hungarian mapping...");
        Hungarian hungarian = new Hungarian(simMat, Hungarian.ProblemType.maxLoc);
        hungarian.run();
        int[] res = hungarian.getResult();
        // map
        HashMap<Integer, String> rowIndexNameMap = simMat.getRowIndexNameMap();
        HashMap<Integer, String> colIndexNameMap = simMat.getColIndexNameMap();
        HashMap<String, String> initMap = new HashMap<>();
        for (int i = 0; i < res.length; i++) {
            int j = res[i];
            if (j == -1) {
                continue;
            }
            initMap.put(rowIndexNameMap.get(i), colIndexNameMap.get(j));
        }
        return initMap;
    }

    /**
     * divide S(t)
     * into two matrixes: the H-matrix, and the G-matrix, which
     * collects the remaining entries of S(t)
     *
     * @param toMap      matrix for hga mapping
     * @param splitLimit if index graph nodes is less than this limit, use the hungarian directly
     */
    protected HashMap<String, String> remapping(SimMat toMap, int splitLimit) {
        assert (toMap != null);
        logInfo("Selecting " + hAccount * 100 + "% of rows for Hungarian allocation, and the left " + (100 - hAccount * 100) + "% for Greedy mapping.");
        if (udG1.getAllNodes().size() < splitLimit && udG2.getAllNodes().size() < splitLimit) {
            return getMappingFromHA(toMap);
        } else {
            Pair<SimMat, SimMat> res = toMap.splitByPercentage(hAccount);
            // check
            SimMat H = res.getFirst();
            SimMat G = res.getSecond();
            HashMap<String, String> mapping = getMappingFromHA(H);
            greedyMap(G, mapping);
            return mapping;
        }

    }

    /**
     * Greedily map the maximum value for each rows in the G matrix.
     */
    public static void greedyMap(SimMat toMap, HashMap<String, String> preMap) {
        HashMap<Integer, String> rowMap = toMap.getRowIndexNameMap();
        HashSet<String> assign = new HashSet<>(preMap.values());
        // no parallel here, assign is stateful
        rowMap.keySet().forEach(i -> {
            String tgt = rowMap.get(i);
            if (!preMap.containsKey(tgt)) {
                String mapStr = toMap.getMax(i, assign);
                preMap.put(tgt, mapStr);
            }
        });
    }


    /**
     * Step 2:
     * The similarities of neighbors for each pair of matching
     * nodes (up, vq) are then rewarded with a positive number
     * ω, leading to an updated similarity matrix
     * <br>
     * <p>w is defined as the sim(u,v)/NA(a)</p>
     * <p>where u,v  is nodes from graph1,and graph2</p>
     * <p>a is one of the neighbors of the node u</p>
     * <p>NA(a) represents the degree of the node a</p>
     * <br>
     *
     * <p>
     * NOTICE:The matrix of the adjList will be synchronized at the same time
     * </p>
     *
     * @param mapping current mapping result, and one edge means the srcNode and tgtNode has already mapped, srcNode ->graph1, tgtNode -> graph2
     */
    protected void updatePairNeighbors(HashMap<String, String> mapping) {
        logInfo("adjust neighborhood similarity based on mapping result...");
        NBM.neighborSimAdjust(udG1, udG2, simMat, mapping);
    }

    /**
     * Step 3.1:
     * Adding topology information:
     * Given any two nodes ui, vj in the networks A and B,
     * respectively, their topological similarities are computed
     * based on an approach previously used for the topological
     * similarity of bio-molecular networks.which we have
     * called the topological similarity parameter (TSP). The
     * TSP includes θij 1 and θij 2 , which are updated according
     * to the rule that two nodes are similar if they link or do
     * not link to similar nodes
     * <br>
     * <br>
     * <p>S(ij)n (one element of the matrix i row j col) in the n time's iteration :</p>
     * S(ij)t = s(ij)1 + 1/2*(θij 1 + θij 2)
     * <br>
     * <p>
     * θij 1:represents the average similarity between the neighbors of ui and vj,
     * </p>
     * <br>
     * <p>
     * θij 2:represents the average similarity between the non-neighbors of ui and vj.
     * </p>
     *
     * @param node1 one node from the graph1
     * @param node2 one node from the graph2
     */
    public static void addTopology(String node1, String node2, SimMat preMat, SimMat originalMat) {
        Set<String> neighbors_1 = udG1.getNebs(node1);
        Set<String> neighbors_2 = udG2.getNebs(node2);
        // compute topologyInfo
        double eNeighbors = getNeighborTopologyInfo(neighbors_1, neighbors_2, preMat);
        // add node1,node2
        neighbors_1.add(node1);
        neighbors_2.add(node2);
        double eNonNeighbors = getNonNeighborTopologyInfo(neighbors_1, neighbors_2, preMat);
        double eTP = (eNeighbors + eNonNeighbors) / 2;
        double valToUpdate = originalMat.getVal(node1, node2) * bioFactor + eTP * (1 - bioFactor);
        simMat.put(node1, node2, valToUpdate);
    }

    public static double getNonNeighborTopologyInfo(Set<String> nei1, Set<String> nei2, SimMat preMat) {
        AtomicReference<Double> res = new AtomicReference<>((double) 0);
        HashSet<String> nodes1 = udG1.getAllNodes();
        HashSet<String> nodes2 = udG2.getAllNodes();
        int nonNei1Size = nodes1.size() - nei1.size();
        int nonNei2Size = nodes2.size() - nei2.size();

        if (nonNei1Size == 0 && nonNei2Size == 0) {
            int size = nodes1.size() * nodes2.size();
            return sumPreSimMat / size;
        }
        // get the rest nodes
        nodes1.removeAll(nei1);
        nodes2.removeAll(nei2);
        if (nonNei1Size != 0 && nonNei2Size != 0) {
            int size = nonNei1Size * nonNei2Size;
            nodes1.forEach(node1 -> nodes2.forEach(node2 -> {
                res.updateAndGet(v -> v + preMat.getVal(node1, node2));
            }));
            return res.get() / size;
        }
        // one size is all connected point
        return res.get();
    }

    // return score and sum of neighbors
    public static double getNeighborTopologyInfo(Set<String> nei1, Set<String> nei2, SimMat preMat) {
        AtomicReference<Double> score = new AtomicReference<>((double) 0);
        HashSet<String> nodes1 = udG1.getAllNodes();
        HashSet<String> nodes2 = udG2.getAllNodes();
        int nei1Size = nei1.size();
        int nei2Size = nei2.size();
        if (nei1Size != 0 && nei2Size != 0) {
            int size = nei1Size * nei2Size;
            nei1.forEach(node1 -> nei2.forEach(node2 ->
                    score.updateAndGet(v -> v + preMat.getVal(node1, node2))));
            return score.get() / size;
        }
        if (nei1Size == 0 && nei2Size == 0) {
            int size = nodes1.size() * nodes2.size();
            double sum = sumPreSimMat;
            return sum / size;
        }
        // one size is isolated point
        return score.get();
    }

    /**
     * Step 3 - integrated all steps in process 3(Topology info):
     * iterate all nodes pairs to add topological information
     * Notice: the result would be different when
     */
    protected static void addAllTopology(SimMat originalMat) {
        Set<String> nodes1 = simMat.getRowMap().keySet();
        Set<String> nodes2 = simMat.getColMap().keySet();
        // parallel the rows
        // https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
        // similarity matrix after the neighborhood adjustment
        SimMat preSimMat = (SimMat) simMat.dup();
        sumPreSimMat = preSimMat.getMat().sum();
        // when index graph nodes scale is less than LIMIT then HGA uses parallel CPU instead
        // && nodes1.size() > LimitOfIndexGraph
        if (GPU) {
            logInfo("AddTopology for all nodes pairs in two graphs with the GPU programming:");
            gpuforhga(nodes1, nodes2, preSimMat);
        } else {
            logInfo("AddTopology for all nodes pairs in two graphs with the CPU parallel programming:");
            nodes1.parallelStream().forEach(n1 -> nodes2.forEach(n2 -> addTopology(n1, n2, preSimMat, originalMat)));
        }
    }

    private static void gpuforhga(Set<String> nodes1, Set<String> nodes2, SimMat preMat) {
        // prepare the input indexes for all neighbors and all non neighbors
        int size = nodes1.size() * nodes2.size();
        // use 1D to save overhead and float instead of double, because the tolerance is 0.01
//        float[][] neighbors = new float[size][size];
//        float[][] nonNeighbors = new float[size][size];
        float[] neighbors = initMatrixGPU_neis(nodes1,nodes2);
        float[] nonNeighbors = initMatrixGPU_nonNeis(nodes1,nodes2);
        // norm size
        int[] neiSize = new int[size];
        int[] nonNeiSize = new int[size];
        // start point
        int[] start_nei = new int[size];
        int[] start_nonNei = new int[size];
        float[] out = new float[size];

        // p1 for every item in the matrix
        AtomicInteger c = new AtomicInteger();
        // p2 for every item in the float[]
        AtomicInteger p_nei = new AtomicInteger();
        AtomicInteger p_nonNei = new AtomicInteger();
        nodes1.forEach(n1 -> nodes2.forEach(n2 -> {
            int p1 = c.get();
            HashSet<String> nodesG1 = new HashSet<>(nodes1);
            HashSet<String> nodesG2 = new HashSet<>(nodes2);
            // neighbors
            Set<String> nei1 = udG1.getNebs(n1);
            Set<String> nei2 = udG2.getNebs(n2);
            int nei1Size = nei1.size();
            int nei2Size = nei2.size();
            if (nei1Size != 0 && nei2Size != 0) {
                int norm = nei1.size() * nei2.size();
                nei1.forEach(node1 -> nei2.forEach(node2 -> {
                    neighbors[p_nei.get()] = (float) preMat.getVal(node1, node2);
                    p_nei.getAndIncrement();
                }));
                neiSize[p1] = norm;
            }
            if (nei1Size == 0 && nei2Size == 0) {
                neighbors[p_nei.get()] = (float) sumPreSimMat;
                p_nei.getAndIncrement();
                neiSize[p1] = size;
            }
            // one side is isolated point
            else{
                neighbors[p_nei.get()] = 0;
                p_nei.getAndIncrement();
                neiSize[p1] = 1;
            }
            // non-neighbors
            nei1.add(n1);
            nei2.add(n2);
            int nonNei1Size = nodes1.size() - nei1.size();
            int nonNei2Size = nodes2.size() - nei2.size();
            if (nonNei1Size == 0 && nonNei2Size == 0) {
                nonNeighbors[p_nonNei.get()] = (float) (sumPreSimMat / size);
                p_nonNei.getAndIncrement();
                nonNeiSize[p1] = size;
            }
            // get the rest nodes
            nodesG1.removeAll(nei1);
            nodesG2.removeAll(nei2);
            if (nonNei1Size != 0 && nonNei2Size != 0) {
                int norm = nodesG1.size() * nodesG2.size();
                nodesG1.forEach(node1 -> nodesG2.forEach(node2 -> {
                    nonNeighbors[p_nonNei.get()] = (float) preMat.getVal(node1, node2);
                    p_nonNei.getAndIncrement();
                }));
                nonNeiSize[p1] = norm;
            }
            // one side all connected
            else{
                nonNeighbors[p_nonNei.get()] = 0;
                p_nonNei.getAndIncrement();
                nonNeiSize[p1] = 1;
            }
            // out
            out[p1] = (float) originalMat.getVal(n1, n2);
            // start
            if(p1==0){
                start_nei[0] = 0;
                start_nonNei[0] = 0;
            }
            else{
                start_nei[p1] = start_nei[p1-1]+ neiSize[p1-1];
                start_nonNei[p1] = start_nonNei[p1-1]+ nonNeiSize[p1-1];
            }
            c.getAndIncrement();
        }));
        GPUKernelForHGA kernel = new GPUKernelForHGA(neighbors, nonNeighbors,
                start_nei,start_nonNei,
                neiSize,nonNeiSize,
                out,
                (float) bioFactor);
        Device device = Device.bestGPU();
        Range range = device.createRange(nodes1.size() * nodes2.size());
        kernel.execute(range);
        kernel.dispose();
        // turn out into simMat
        AtomicInteger c2 = new AtomicInteger();
        nodes1.forEach(n1 -> nodes2.forEach(n2 -> {
            simMat.put(n1, n2, out[c2.get()]);
            c2.getAndIncrement();
        }));
    }

    private static float[] initMatrixGPU_nonNeis(Set<String> nodes1, Set<String> nodes2) {
        AtomicInteger sum = new AtomicInteger();
        nodes1.forEach(n1 -> nodes2.forEach(n2 -> {
            Set<String> nei1 = udG1.getNebs(n1);
            Set<String> nei2 = udG2.getNebs(n2);
            int s1 = nodes1.size()-nei1.size()-1;
            int s2 = nodes2.size()-nei2.size()-1;
            int size = 0;
            // all connected
            if(s1==0&&s2==0){
                size = 1;
            }
            // non neighbors exist
            else if(s1!=0&&s2!=0){
                size = s1*s2;
            }
            // only one side all connected -> sim() = 0
            else{
                size = 1;
            }
            sum.addAndGet(size);
        }));
        return new float[sum.get()];
    }

    private static float[] initMatrixGPU_neis(Set<String> nodes1, Set<String> nodes2) {
        AtomicInteger sum = new AtomicInteger();
        nodes1.forEach(n1 -> nodes2.forEach(n2 -> {
            Set<String> nei1 = udG1.getNebs(n1);
            Set<String> nei2 = udG2.getNebs(n2);
            int size = 0;
            int s1 = nei1.size();
            int s2 = nei2.size();
            if(s1!=0 && s2!=0){
                size = s1*s2;
            }
            else if(s1 ==0 && s2 == 0){
                size = 1;
            }
            else{
                size = 1;
            }
            sum.addAndGet(size);
        }));
        return new float[sum.get()];
    }


    private List<Triple<String, String, Double>> sortToPair(Set<String> nodes1, Set<String> nodes2) {
        Vector<Triple<String, String, Double>> sortPairForTopo = new Vector<>();
        // parallel here there is no interference and no stateful lambda
        // https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
        nodes1.parallelStream().forEach(node1 -> nodes2.forEach(node2 ->
                sortPairForTopo.add(new Triple<>(node1, node2, simMat.getVal(node1, node2)))));
        List<Triple<String, String, Double>> res = sortPairForTopo.stream().sorted(Comparator.comparingDouble(Triple::getThird)).collect(Collectors.toList());
        Collections.reverse(res);
        return res;
    }

    /**
     * This step is used to score current mapping to indicate whether there's
     * a need to adjust and map again
     * <br>
     * <p>The following params for evaluate the whole network mapping</p>
     *     <ol>
     *         <li>EC : Edge Correctness (EC) is often used to measure
     * the degree of topological similarity and
     * can be estimated as the percentage of matched edges</li>
     *
     *          <li>
     *              PE: Point and Edge Score(PE) is clearly stricter than EC because it reflects the status
     *              of both the node and edge matches in the mapping.
     *          </li>
     *
     *          <li>
     *               The score for an edge (the Edge Score, ES) equals zero if any of its nodes does not match
     *               with its similar nodes, and the score for a node (the Point Score, PS) equals zero if none of its edges has a score.
     *          </li>
     *     </ol>
     */
    public static void scoreMapping(HashMap<String, String> mapping) {
        logInfo("Scoring for mapping ...");

        // edge correctness EC
        mappingEdges = setEC(mapping);
        // point and edge score PE
        ES = getES(mappingEdges);
        PS = getPS(mappingEdges);
        PE = ES / 2 + PS;
        score = 100 * EC + PE;
    }

    private static double getES(Vector<Pair<Edge, Edge>> mappingEdges) {

        AtomicReference<Double> ES = new AtomicReference<>((double) 0);
        for (Iterator<Pair<Edge, Edge>> iterator = mappingEdges.iterator(); iterator.hasNext(); ) {
            Pair<Edge, Edge> map = iterator.next();
            Edge edge1 = map.getFirst();
            Edge edge2 = map.getSecond();
            if (simMat.getVal(edge1.getSource().getStrName(), edge2.getSource().getStrName()) > 0 &&
                    simMat.getVal(edge1.getTarget().getStrName(), edge2.getTarget().getStrName()) > 0) {
                ES.updateAndGet(v -> v + edgeScore);
            } else {
                iterator.remove();
            }
        }
        return ES.get();
    }

    /**
     * @param mappingEdges getES() filters out unqualified edges
     */
    protected static double getPS(Vector<Pair<Edge, Edge>> mappingEdges) {
        // parallel here there is no interference and no stateful lambda
        //https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
        AtomicReference<Double> PS = new AtomicReference<>((double) 0);
        mappingEdges.parallelStream().forEach(map -> {
            Edge edge1 = map.getFirst();
            Edge edge2 = map.getSecond();
            String n1_1 = edge1.getSource().getStrName();
            String n1_2 = edge1.getTarget().getStrName();
            String n2_1 = edge2.getSource().getStrName();
            String n2_2 = edge2.getTarget().getStrName();
            PS.updateAndGet(v -> v + simMat.getVal(n1_1, n2_1) + simMat.getVal(n1_2, n2_2));
        });
        return PS.get();
    }


    /**
     * @return set edge correctness and mapping edges[Pair:{graph1Source,graph1Target},{graph2Source,graph2Target}]
     */
    public static Vector<Pair<Edge, Edge>> setEC(HashMap<String, String> mapping) {
        mappingEdges = new Vector<>();
        // toMap will decrease when nodes have been checked
        HashSet<String> toMap = new HashSet<>(mapping.keySet());
        AtomicInteger count = new AtomicInteger();
        for (Iterator<String> iterator = toMap.iterator(); iterator.hasNext(); ) {
            String n1 = iterator.next();
            String n1_ = mapping.get(n1);
            iterator.remove();
            Set<String> nebs = udG1.getNebs(n1);
            // parallel here there is no interference and no stateful lambda
            //https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
            // overlap -> one edge in graph1(contains n1 as one node)
            Collection<String> edge1s = nebs.parallelStream().filter(toMap::contains).collect(Collectors.toList());
            if (edge1s.size() == 0) {
                continue;
            }
            // parallel here there is no interference and no stateful lambda
            //https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
            // check graph2 -> have the corresponding "edge"
            edge1s.parallelStream().forEach(n2 -> {
                String n2_ = mapping.get(n2);
                if (udG2.getNebs(n1_).contains(n2_)) {
                    count.getAndIncrement();
                    mappingEdges.add(new Pair<>(new Edge(n1, n2), new Edge(n1_, n2_)));
                }
            });
        }
        EC = (double) count.get() / udG1.getEdgeCount();
        return mappingEdges;
    }


    /**
     * Step 4: - check if the condition is passed
     * Continue to step 2 until one of the following conditions
     * is satisfied:
     * | Si - Si-1 | < r
     * | Si - Si-2 | < r
     * A sum score does not change in three continuous iterations.
     * || -> determinant of matrix
     * ------------------------------------------
     * r = 0.01 to allow 1% error
     */
    protected boolean checkPassed(double tolerance) {

        if (stackMat.size() == 3) {
            if (iterCount > iterMax) {
                return true;
            }
            DoubleMatrix s1 = stackMat.get(1);
            DoubleMatrix s = stackMat.peek();
            // remove bottom which is the oldest for every iteration
            DoubleMatrix s2 = stackMat.remove(0);

            double score = stackScore.peek();
            double score1 = stackScore.get(1);
            double score2 = stackScore.remove(0);

            double dif_1 = s.sub(s1).normmax();
            double dif_2 = s.sub(s2).normmax();
            logInfo("Iteration:" + iterCount + "\tdif_1 " + dif_1 + "\t" + "dif_2 " + dif_2 + "\nScore:" + "score1 " + score1
                    + "\t" + "score2 " + score2);
            return dif_1 < tolerance || dif_2 < tolerance ||
                    (score == score1 && score1 == score2);

        }
        // size = 2
        else if (stackMat.size() == 2) {
            DoubleMatrix s = stackMat.peek();
            double dif = s.sub(stackMat.get(0)).normmax();
            logInfo("Iteration:" + iterCount + "\tdif " + dif);
            return dif < tolerance;
        }
        // size = 1
        return false;

    }

    public void run() {
        if (debugOut) {
            cleanDebugResult();
        }
        logInfo("Init mapping...");
//        Pair<SimMat, HashMap<String, String>> init = initMap();
        Pair<HashMap<String, String>, SimMat> init = getRemapForForced();
        // iterate
        hgaIterate(mapping, simMat, init.getSecond(), init.getFirst()
                , iterCount, score, PE, EC, PS, ES);

    }

    private Pair<SimMat, HashMap<String, String>> initMap() {
        // stacks for simMat converge
        stackMat = new Stack<>();
        stackScore = new Stack<>();
        HashMap<String, String> remapPart;
        HashMap<String, String> forcedPart;// forced mapping
        if (forcedMappingForSame) {
            Pair<HashMap<String, String>, SimMat> res = getRemapForForced();
            // hungarian for the res
            remapPart = getMappingFromHA(res.getSecond());
            // forced
            forcedPart = res.getFirst();
            // mapping
            mapping = new HashMap<>(remapPart);
            mapping.putAll(forcedPart);
        } else {
            // get the initial similarity matrix S0
            remapPart = getMappingFromHA(simMat);
            forcedPart = null;
            mapping = new HashMap<>(remapPart);
        }
        SimMat toRemap = simMat.getPart(remapPart.keySet(), remapPart.values());
        // getMatrix return the quoted mat from simMat, should be copied
        stackMat.push(simMat.getMat().dup());
        // add to stack top
        scoreMapping(mapping);
        // record score
        stackScore.push(score);
        // debug
        outDebug();
        return new Pair<>(toRemap, forcedPart);
    }

    private void hgaIterate(HashMap<String, String> mapping, SimMat simMat,
                            SimMat toRemap, HashMap<String, String> forcedPart, int iterCount, double... scores) {
        initScores(scores);
        score_res = score;
        HGA.mapping = mapping;
        HGA.simMat = simMat;
        this.iterCount = iterCount;

        stackMat = new Stack<>();
        stackScore = new Stack<>();
        boolean checkPassed;
        do {
            // log if needed
            logInfo("------------Iteration " + this.iterCount + "/1000------------");
            // step 1 map again
            HGA.mapping = remap(toRemap, forcedPart);
            // step 2 score the mapping
            scoreMapping(HGA.mapping);
            // record
            stackMat.push(simMat.getMat().dup());
            stackScore.push(score);
            // output
            outDebug();
            // step 3 update based on mapped nodes
            updatePairNeighbors(HGA.mapping);
            // step 4 topo adjustment to similarity matrix
            addAllTopology(originalMat);
            this.iterCount++;
            // record best
            if (score > score_res) {
                setUpResult();
            }
            checkPassed = checkPassed(tolerance);
        } while (!checkPassed);
        // output result
        logInfo("HGA mapping finish!With iteration " + this.iterCount + " times.");
        outPutResult();
    }

    /**
     * @return full mapping result
     */
    private HashMap<String, String> remap(SimMat toRemap, HashMap<String, String> forced) {
//        int h = getHByAccount();
        logInfo("Remapping...");
        // regain from file, and there is no remap part, retain.
        if (toRemap == null) {
            toRemap = simMat;
        }
        // hungarian account
        HashMap<String, String> res = remapping(toRemap, splitLimit);
        // not forced
        if (forced == null) {
            return res;
        }
        res.putAll(forced);
        return res;
    }

    /**
     * get h standard based on the account for hungarian user has input
     *
     * @return non-zeros selection standard h
     */
    int getHByAccount() {
        // get non-zeros number by rows and sort it
        Vector<Integer> nonZeros = new Vector<>();
        simMat.getNonZerosIndexMap().values().parallelStream().forEach(set -> nonZeros.add(set.size()));
        List<Integer> nonZerosNumbs = nonZeros.stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        int limit = (int) (nonZerosNumbs.size() * (1 - hAccount));
        return nonZerosNumbs.get(limit);
    }


    /**
     * @return forceMap, remap
     */
    private Pair<HashMap<String, String>, SimMat> getRemapForForced() {
        // row to map
        Set<String> rowToMap = simMat.getRowSet();
        rowToMap.removeAll(simMat.getColSet());
        // col to map
        Set<String> colToMap = simMat.getColSet();
        colToMap.removeAll(simMat.getRowSet());
        SimMat remap = simMat.getPart(rowToMap, colToMap);
        HashMap<String, String> forceMap = null;
        if (forcedMappingForSame) {
            // set up force mapping
            HashSet<String> sameNodes = simMat.getRowSet();
            sameNodes.retainAll(simMat.getColSet());
            forceMap = new HashMap<>();
            HashMap<String, String> finalForceMap1 = forceMap;
            sameNodes.parallelStream().forEach(n -> finalForceMap1.put(n, n));
        }
        return new Pair<>(forceMap, remap);
    }

    private static void logInfo(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }


    public void outDebug() {
        if (debugOut) {
            outPutMatrix(simMat.getMat(), false);
            outPutScoring(false, score, PE, EC, ES, PS);
            outPutMapping(mapping, false);
        }
    }

    public void outPutMatrix(DoubleMatrix mat, boolean isResult) {
        logInfo("output matrix");
        String path = debugOutputPath + "matrix/";
        Vector<String> matrixVec = new Vector<>();
        double[][] mat_ = mat.toArray2();
        for (double[] doubles : mat_) {
            for (int j = 0; j < mat_[0].length; j++) {
                matrixVec.add(doubles[j] + " ");
            }
            matrixVec.add("\n");
        }
        try {
            if (isResult) {
                writer.setPath(path + "matrixResult_" + iter_res + ".txt");
            } else {
                writer.setPath(path + "matrix_" + iterCount + ".txt");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        writer.write(matrixVec, false);
    }

    public void outPutScoring(boolean isResult, double... scores) {
        logInfo("output scores");
        String path = debugOutputPath + "scoring/";
        Vector<String> scoreVec = new Vector<>();

        scoreVec.add("Iteration " + iterCount + ":\n");
        scoreVec.add("Score:" + scores[0] + "\n");
        scoreVec.add("PE:" + scores[1] + "\n");
        scoreVec.add("EC:" + scores[2] + "\n");
        scoreVec.add("ES:" + scores[3] + "\n");
        scoreVec.add("PS:" + scores[4] + "\n");
        try {
            if (isResult) {
                writer.setPath(path + "scoringResult_" + iterCount + ".txt");
            } else {
                writer.setPath(path + "scoring_" + iterCount + ".txt");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        writer.write(scoreVec, false);
    }

    public void outPutMapping(HashMap<String, String> mapping, boolean isResult) {
        logInfo("output mapping");
        String path = debugOutputPath + "mapping/";
        Vector<String> mappingVec = new Vector<>();
        mapping.forEach((k, v) -> mappingVec.add(k + " " + v + "\n"));
        try {
            if (isResult) {
                writer.setPath(path + "mappingResult_" + iterCount + ".txt");
            } else {
                writer.setPath(path + "mapping_" + iterCount + ".txt");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        writer.write(mappingVec, false);
    }

    void cleanDebugResult() {
        debugOutputPath = System.getProperty("user.dir").replace('/', '\\') + "\\" + debugOutputPath;
        // use '\' to fit with linux
        debugOutputPath = debugOutputPath.replace('\\', '/');
        String mapping = debugOutputPath + "mapping";
        String scoring = debugOutputPath + "scoring";
        String matrix = debugOutputPath + "matrix";
        deleteAllFiles(mapping);
        deleteAllFiles(scoring);
        deleteAllFiles(matrix);
    }

    private void deleteAllFiles(String directory) {
        try {
            FileUtils.cleanDirectory(new File(directory));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setUpResult() {
        ES_res = ES;
        PE_res = PE;
        PS_res = PS;
        EC_res = EC;
        score_res = score;
        iter_res = iterCount;
        mappingResult = new HashMap<>(mapping);
        matrix_res = simMat.getMat().dup();
    }

    private void initScores(double... scores) {
        score = scores[0];
        PE = scores[1];
        EC = scores[2];
        ES = scores[3];
        PS = scores[4];
    }

    public void outPutResult() {
        if (debugOut) {
            outPutMapping(mappingResult, true);
            outPutMatrix(matrix_res, true);
            outPutScoring(true, score_res, PE_res, EC_res, ES_res, PS_res);
        }

    }
//TODO retain
//    /**
//     * If data has been lost for some reasons, user can
//     * retain the process they have been on going.
//     */
//    public void retain(String matrixPath,String scorePath,String mappingPath) throws IOException {
//        DoubleMatrixReader matrixReader = new DoubleMatrixReader(matrixPath);
//        DoubleMatrix mat = matrixReader.getMat();
//        SimMat simMat = new SimMat(,,mat);
//        hgaIterate();
//    }

    public double getES_res() {
        return ES_res;
    }

    public double getPE_res() {
        return PE_res;
    }

    public double getPS_res() {
        return PS_res;
    }

    public double getScore_res() {
        return score_res;
    }

    public DoubleMatrix getMatrix_res() {
        return matrix_res;
    }


    public double getEC_res() {
        return EC_res;
    }

    public double getES() {
        return ES;
    }

    public double getPS() {
        return PS;
    }

    public double getPE() {
        return PE;
    }

    public double getEC() {
        return EC;
    }

    public double getScore() {
        return score;
    }

    public int getIter_res() {
        return iter_res;
    }

    public HashMap<String, String> getMapping() {
        return mapping;
    }

    public HashMap<String, String> getMappingResult() {
        return mappingResult;
    }


    public void setBioFactor(double bioFactor) {
        assert (bioFactor >= 0 && bioFactor <= 1);
        HGA.bioFactor = bioFactor;
    }

    public void setForcedMappingForSame(boolean forcedMappingForSame) {
        HGA.forcedMappingForSame = forcedMappingForSame;
    }

    public void setEdgeScore(double edgeScore) {
        HGA.edgeScore = edgeScore;
    }

    public int getH() {
        return h;
    }

    public void setH(int h) {
        HGA.h = h;
    }

    public void sethAccount(double hAccount) {
        assert (hAccount >= 0 && hAccount <= 1);
        HGA.hAccount = hAccount;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    public void setupLogger() throws IOException {
        logger = Logger.getLogger("MyLog");
        FileHandler fh;
        fh = new FileHandler("HGALogFile.log" + debugOutputPath.split("data")[1]);
        fh.setFormatter(new SimpleFormatter());
        logger.addHandler(fh);
        // output matrix, scoring and mapping result
        writer = new AbstractFileWriter() {
            @Override
            public void write(Vector<String> context, boolean closed) {
                super.write(context, false);
            }
        };

    }

    public static void setUdG1(UndirectedGraph udG1) {
        HGA.udG1 = udG1;
    }

    public static void setUdG2(UndirectedGraph udG2) {
        HGA.udG2 = udG2;
    }

    public void setIterMax(int iterMax) {
        this.iterMax = iterMax;
    }

    public Vector<Pair<Edge, Edge>> getMappingEdges() {
        return mappingEdges;
    }
}

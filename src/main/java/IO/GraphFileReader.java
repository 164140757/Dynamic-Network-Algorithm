package IO;

import Algorithms.Graph.Utils.AdjList.Graph;
import Algorithms.Graph.Utils.AdjList.SimList;
import Algorithms.Graph.Utils.SimMat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This class is mean to create a FileReader aimed to read file with the format like the adjacent list
 * or create a matrix.
 * </p>
 * <br>
 * <p>
 * source Node : target Node(i) weight(i)
 * </p>
 *
 * <p>
 * where the weight means the edge weight between node(i) and source node.
 * </p>
 * <br>
 */
public class GraphFileReader extends AbstractFileReader {
    private HashSet<String> sourceNodesSet;
    private HashSet<String> targetNodesSet;
    // get neighbors information
    private HashMap<String, HashSet<String>> graphNeighbors;
    // non zeros
    private ArrayList<Integer> nonZerosMap;
    // max for Row
    private ArrayList<Double> maxValForRow;
    //--------------------
    private boolean recordMaxForRow;
    private boolean recordNeighbors;
    private boolean recordSrcAndTarget;
    private boolean recordNonZeros;
    //------------max---------------
    // for a row
    private double maxOfRow = -Double.MAX_VALUE;
    private String maxNodeName = null;
    private int rowIndex = 0;
    private int nonZeroCount;

    public GraphFileReader(boolean recordSrcAndTarget, boolean getNeighbors, boolean getMaxForRow, boolean recordNonZeros) {
        super();
        this.recordSrcAndTarget = recordSrcAndTarget;
        this.recordMaxForRow = getMaxForRow;
        this.recordNeighbors = getNeighbors;
        this.recordNonZeros = recordNonZeros;
    }
    //-------------------------AdjNodeList【homoGeneMap】 return type has been added in switch choices------------------------

    public Graph readToGraph(String path) {
        return null;
    }

    public SimList readToSimList(String inputFilePath) throws IOException {

        return readToSimList(new BufferedReader(new FileReader(inputFilePath)), true);
    }

    public SimList readToSimList(String inputFilePath, boolean closeWhenFinished) throws IOException {

        return readToSimList(new BufferedReader(new FileReader(inputFilePath)), closeWhenFinished);
    }

    /**
     * Parses a arrayList format file to ArrayList.
     *
     * @param input             the reader to read the SIF file from
     * @param closeWhenFinished if true, this method will close
     *                          the reader when finished reading; otherwise, it will
     *                          not close it.
     */
    private SimList readToSimList(BufferedReader input, boolean closeWhenFinished) throws IOException {
        init();
        SimList simList = new SimList();
        // matches sequence of one or more whitespace characters.
        setSplitter("\\s+");
        Vector<String> sifLine = new Vector<>();
        String line;
        while ((line = input.readLine()) != null) {
            String[] tokens = splitter.split(line);
            if (tokens.length == 0) continue;
            //  it will be handled in pareLine()
            // which will throw an IOException if not the right case.
            for (String token : tokens) {
                if (token.length() != 0) {
                    sifLine.add(token);
                }
            }
            parseForSimList(simList, sifLine);
            // clean for each line
            cleanLine();
        }
        if (closeWhenFinished) {
            input.close();
        }
        // simList setting up

        return simList;
    }

    private SimMat readToSimMat(BufferedReader input,HashSet<String> graph1Nodes,HashSet<String> graph2Nodes, boolean closeWhenFinished) throws IOException {
        init();
        SimMat simMat = new SimMat(graph1Nodes,graph2Nodes);
        // matches sequence of one or more whitespace characters.
        setSplitter("\\s+");
        Vector<String> sifLine = new Vector<>();
        String line;
        while ((line = input.readLine()) != null) {
            String[] tokens = splitter.split(line);
            if (tokens.length == 0) continue;
            //  it will be handled in pareLine()
            // which will throw an IOException if not the right case.
            for (String token : tokens) {
                if (token.length() != 0) {
                    sifLine.add(token);
                }
            }
            parseForSimMat(simMat, sifLine);
            // clean for each line
            cleanLine();
        }
        if (closeWhenFinished) {
            input.close();
        }
        return simMat;
    }



    /**
     * Parses a arrayList format file to ArrayList.
     *
     * @param input             the reader to read the SIF file from
     * @param closeWhenFinished if true, this method will close
     *                          the reader when finished reading; otherwise, it will
     *                          not close it.
     */
    private Graph readToGraph(BufferedReader input, boolean closeWhenFinished) throws IOException {
        init();
        Graph graph = new Graph();
        // matches sequence of one or more whitespace characters.
        setSplitter("\\s+");
        Vector<String> sifLine = new Vector<>();
        String line;
        while ((line = input.readLine()) != null) {
            String[] tokens = splitter.split(line);
            if (tokens.length == 0) continue;
            //  it will be handled in pareLine()
            // which will throw an IOException if not the right case.
            for (String token : tokens) {
                if (token.length() != 0) {
                    sifLine.add(token);
                }
            }
            parseForGraph(graph, sifLine);
            // clean for each line
            cleanLine();
        }
        if (closeWhenFinished) {
            input.close();
        }
        return graph;
    }

    private void parseForGraph(Graph graph, Vector<String> sifLine) {

    }


    private void cleanLine() {
        maxNodeName = null;
        maxOfRow = -Double.MAX_VALUE;
        nonZeroCount = 0;
    }

    private void init() {
        // row index
        rowIndex = 0;
        // clean or init
        if (recordSrcAndTarget) {
            sourceNodesSet = new HashSet<>();
            targetNodesSet = new HashSet<>();
        }
        if (recordNeighbors) {
            graphNeighbors = new HashMap<>();
        }
        if(recordNonZeros){
            nonZerosMap = new ArrayList<>();
        }
        if(recordMaxForRow){
            maxValForRow = new ArrayList<>();
        }
    }


    /**
     * <ol>
     *     <li>node1 node2 value12</li>
     *     <li>node2 node3 value23 node4 value24 node5 value25</>
     *     <li>node0 value00</li>
     *  </ol>
     * <p>
     *     The first line identifies two nodes, called node1 and node2, and the weight of the edge between node1 node2. The second line specifies three new nodes, node3, node4, and node5; here “node2” refers to the same node as in the first line.
     *     The second line also specifies three relationships, all of the individual weight and with node2 as the source, with node3, node4, and node5 as the targets.
     *     This second form is simply shorthand for specifying multiple relationships of the same type with the same source node.
     *     The third line indicates how to specify a node that has no relationships with other nodes.
     * </p>
     *
     * <p>NOTICE:add() -> use sortAdd()</p>
     *
     * @param simList   EdgeList to contain result
     * @param sifLine result very line
     */
    private void parseForSimList(SimList simList, Vector<String> sifLine) throws IOException {
        // row index
        int row = -1;

        int sifSize = sifLine.size();
        if (sifSize == 0) {
            throw new IOException("Nothing has been input!.");
        }
        if (sifSize == 2) {
            // node1 node1 val1 // a circle
            String name = sifLine.get(0);
            if (isNumeric(sifLine.get(1))) {
                double weight = Double.parseDouble(sifLine.get(1));
                row = simList.sortAddOneNode(name, name, weight);
                // record if required
                record(name,name,weight);
            }
            // node1 node2
            else {
                String targetName = sifLine.get(1);
                if (isIdentifier(targetName)) {
                    row = simList.sortAddOneNode(name, targetName, 0);
                    // record if required
                    record(name,name,0.);
                } else {
                    throw new IOException("nodes' name are not correct.");
                }
            }
        } else if ((sifSize - 1) % 2 != 0 || sifSize == 1) {
            throw new IOException("The file input format is not correct.");
        } else {
            String srcName = sifLine.get(0);
            // name value ... and it has already checked (sifSize -1) % 2 == 0
            for (int index = 1; index < sifSize; index += 2) {
                // name
                String tgtName = sifLine.get(index);
                if (!isIdentifier(tgtName)) {
                    throw new IOException("nodes' name are not correct.");
                }

                // value
                String input = sifLine.get(index + 1);
                if (!isNumeric(input)) {
                    throw new IOException("The file input format is not correct. Plus: some name-value pairs are incorrect!");
                }
                double weight = Double.parseDouble(input);
                // create edge & add
                // Contain the lexicographical order to prevent the case of same edges.
                row = simList.sortAddOneNode(srcName, tgtName, weight);
                // record if required
                record(srcName,tgtName,weight);
            }
        }
        sifLine.clear();
        // set max
        simList.get(row).setMax(maxOfRow);
    }
    /**
     * <ol>
     *     <li>node1 node2 value12</li>
     *     <li>node2 node3 value23 node4 value24 node5 value25</>
     *     <li>node0 value00</li>
     *  </ol>
     * <p>
     *     The first line identifies two nodes, called node1 and node2, and the weight of the edge between node1 node2. The second line specifies three new nodes, node3, node4, and node5; here “node2” refers to the same node as in the first line.
     *     The second line also specifies three relationships, all of the individual weight and with node2 as the source, with node3, node4, and node5 as the targets.
     *     This second form is simply shorthand for specifying multiple relationships of the same type with the same source node.
     *     The third line indicates how to specify a node that has no relationships with other nodes.
     * </p>
     *
     * <p>NOTICE:add() -> use sortAdd()</p>
     *
     * @param simMat   EdgeList to contain result
     * @param sifLine result very line
     */
    private void parseForSimMat(SimMat simMat, Vector<String> sifLine) throws IOException {

        int sifSize = sifLine.size();
        if (sifSize == 0) {
            throw new IOException("Nothing has been input!.");
        }
        if (sifSize == 2) {
            // node1 node1 val1 // a circle
            String name = sifLine.get(0);
            if (isNumeric(sifLine.get(1))) {
                double weight = Double.parseDouble(sifLine.get(1));
                if(weight!=0){
                    simMat.put(name,name,weight);
                }
                // record if required
                record(name,name,weight);
            }
            // node1 node2
            else {
                String targetName = sifLine.get(1);
                if (isIdentifier(targetName)) {
                    // record if required
                    record(name,name,0.);
                } else {
                    throw new IOException("nodes' name are not correct.");
                }
            }
        } else if ((sifSize - 1) % 2 != 0 || sifSize == 1) {
            throw new IOException("The file input format is not correct.");
        } else {
            String srcName = sifLine.get(0);
            // name value ... and it has already checked (sifSize -1) % 2 == 0
            for (int index = 1; index < sifSize; index += 2) {
                // name
                String tgtName = sifLine.get(index);
                if (!isIdentifier(tgtName)) {
                    throw new IOException("nodes' name are not correct.");
                }
                // value
                String input = sifLine.get(index + 1);
                if (!isNumeric(input)) {
                    throw new IOException("The file input format is not correct. Plus: some name-value pairs are incorrect!");
                }
                double weight = Double.parseDouble(input);
                // create edge & add
                // Contain the lexicographical order to prevent the case of same edges.
                if(weight!=0){
                    simMat.put(srcName, tgtName, weight);
                }
                // record if required
                record(srcName,tgtName,weight);
            }
        }
        sifLine.clear();
        // set max
        maxValForRow.add(maxOfRow);
    }

    private void record(String node1, String node2, double val) {
        recordSourceAndTarget(node1,node2);
        recordNeighbors(node1, node2);
        recordMaxOfRow(node2,val);
        recordNoneZeros(node1, node2, val);
        rowIndex++;
    }
    private void recordNoneZeros(String node1,String node2,double val){
        if(recordNonZeros){
            if(val!=0){
                nonZerosMap.set(rowIndex,++nonZeroCount);
            }
        }
    }

    private void recordSourceAndTarget(String node1, String node2) {
        if (recordSrcAndTarget) {
            sourceNodesSet.add(node1);
            targetNodesSet.add(node2);
        }
    }

    private void recordNeighbors(String node1, String node2) {
        if (recordNeighbors) {
            recordMapCheck(node2, node1);
            recordMapCheck(node1, node2);
        }
    }

    private void recordMapCheck(String node1, String node2) {
        if(graphNeighbors.containsKey(node2)){
            HashSet<String> nodes = graphNeighbors.get(node2);
            nodes.add(node1);
            graphNeighbors.put(node2,nodes);
        }
        else{
            graphNeighbors.put(node2,new HashSet<>(Collections.singleton(node1)));
        }
    }

    /**
     * record for a row
     */
    private void recordMaxOfRow(String node, double val) {
        if (recordMaxForRow && val > maxOfRow) {
            maxNodeName = node;
            maxOfRow = val;
        }
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isIdentifier(String str) {
        Pattern p = Pattern.compile("[a-zA-Z][[0-9]|[a-zA-Z]]*");
        Matcher m = p.matcher(str);
        return m.find();
    }

    //------------------PUBLIC-----------------------------

    /**
     * Please execute read instruction before calling this method.
     *
     * @return HashSet of nodes' names in graph_1
     */
    public HashSet<String> getSourceNodesSet() {
        assert (sourceNodesSet != null);
        return sourceNodesSet;
    }

    /**
     * Please execute read instruction before calling this method.
     *
     * @return HashSet of nodes' names in graph_2
     */
    public HashSet<String> getTargetNodesSet() {
        assert (targetNodesSet != null);
        return targetNodesSet;
    }

    public HashMap<String, HashSet<String>> getGraphNeighbors() {
        return graphNeighbors;
    }


    public void setRecordMaxForRow(boolean recordMaxForRow) {
        this.recordMaxForRow = recordMaxForRow;
    }

    public void setRecordNeighbors(boolean recordNeighbors) {
        this.recordNeighbors = recordNeighbors;
    }

    public void setRecordSrcAndTarget(boolean recordSrcAndTarget) {
        this.recordSrcAndTarget = recordSrcAndTarget;
    }

    public void setRecordNonZeros(boolean recordNonZeros) {
        this.recordNonZeros = recordNonZeros;
    }
}

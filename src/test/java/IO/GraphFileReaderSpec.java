package IO;

<<<<<<< Updated upstream:src/test/java/IO/GraphFileReaderSpec.java
import Algorithms.Graph.Utils.AdjList.Graph;
import Algorithms.Graph.Utils.AdjList.SimList;
import Algorithms.Graph.Utils.List.HNodeList;
import Algorithms.Graph.Utils.SimMat;
=======
import Internal.Algorithms.DS.Network.AdjList.DirectedGraph;
import Internal.Algorithms.DS.Network.AdjList.SimList;
import Internal.Algorithms.DS.Network.AdjList.UndirectedGraph;
import Internal.Algorithms.DS.Network.List.HNodeList;
import Internal.Algorithms.DS.Network.SimMat;
>>>>>>> Stashed changes:src/test/java/Internal/Algorithms/IO/GraphFileReaderSpec.java
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("SIFFileReader is")
public class GraphFileReaderSpec {
    SimMat graph;
    GraphFileReader reader;
    @DisplayName("able to use pattern to split. ")
    @Test
    // The first character cannot be space.-> test whether each element for it's length > 0
    void PatternTest() {
        String test = "sp|Q9ZZX9|Q0010_YEAST";
        String test2 = " TMA STD    BBQ";
        Pattern splitter = Pattern.compile("\\s+");
        String[] tokens = splitter.split(test);
        String[] tokens_2 = splitter.split(test2);
        ArrayList<String> tokens_3 = new ArrayList<>();
        for (String str:tokens_2
             ) {
            if(str.length()!=0){
                tokens_3.add(str);
            }
        }
        assertEquals(Arrays.toString(new String[]{"TMA", "STD", "BBQ"}), Arrays.toString(tokens));
        assertNotEquals(Arrays.toString(new String[]{"TMA", "STD", "BBQ"}), Arrays.toString(tokens_2));
        assertEquals(Arrays.toString(new String[]{"TMA", "STD", "BBQ"}), tokens_3.toString());
    }

    @DisplayName("able to read a graph to ArrayList from the specific well-formatting file. ")
    @Test
    void ReadToArrayListTest(){
        try {
            GraphFileReader reader = new GraphFileReader(true,false,true);
            SimList simList = reader.readToSimList("src/test/java/resources/IOTest/simpleGraph_1.txt");
            HNodeList forTestA = new HNodeList("A");
            HNodeList forTestB = new HNodeList("C");
            forTestA.add("B",0.2);
            forTestA.add("C",0.3);
            forTestA.add("D",0.4);
            forTestB.add("A",0.6);
            forTestB.add("B",0.7);
            assertThat(simList).contains(forTestA,forTestB);
            // neighbors, maxVal and src&Target
//            Graph graph = reader.readToGraph("src/test/java/resources/IOTest/simpleGraph_1.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void readSimMatAndGraph() throws IOException {
        GraphFileReader reader = new GraphFileReader(true, true, false);
        Graph graph1 = reader.readToGraph("src/test/java/resources/AlgTest/HGA/graph1.txt", false);
        Graph graph2 = reader.readToGraph("src/test/java/resources/AlgTest/HGA/graph2.txt", false);
        reader.setRecordNonZeros(true);
        reader.setRecordNeighbors(false);
        SimMat simMat = reader.readToSimMat("src/test/java/resources/AlgTest/HGA/simMat.txt", graph1.getAllNodes(), graph2.getAllNodes(), true);

    }

//    @DisplayName("able to read a medium-level graph")
//    @Test
//    void ReadMediumGraph() throws IOException {
//        GraphFileReader reader = new GraphFileReader();
//        SimList graph = reader.readToSimList("src/test/java/resources/IOTest/mediumGraph.txt");
//    }
//    @DisplayName("able to get a HashSet.")
//    @Test
//    void getHashSet() throws IOException {
//        GraphFileReader reader = new GraphFileReader();
//        SimList graph = reader.readToSimList("src/test/java/resources/IOTest/mediumGraph.txt");
//        HashSet<String> graph1 = reader.getSourceNodesSet();
//    }
//
//    @DisplayName("TSV test")
//    @Test
//    void readTSV() throws IOException {
//        GraphFileReader reader = new GraphFileReader();
//        SimList graph = reader.readToSimList("C:\\Users\\Haotian Bai\\Desktop\\cov19\\A1.tsv");
//    }
    // ---------------------------
    // below are some base test class utility methods to validate if the outcome is consistent with your expectation.
//    protected SimList readAndAssertNodeNumber(int expected, String filePath) throws IOException {
//        if(reader == null) reader = new GraphFileReader();
//        if(graph == null) graph = reader.readToGraph(filePath);
//        assertEquals(expected,graph.getAllNodes().size(), "nodes' number is not right.");
//        return graph;
//    }
//    protected SimList readAndAssertEdgeNumber(int expected, String filePath) throws IOException {
//        if(reader == null) reader = new GraphFileReader();
//        if(graph == null) graph = reader.readToSimList(filePath);
//        assertEquals(expected,graph.getAllEdges().size(), "edges' number is not right.");
//        return graph;
//    }

}

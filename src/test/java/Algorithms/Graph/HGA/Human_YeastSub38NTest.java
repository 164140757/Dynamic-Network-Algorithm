/*
 * @Author: Haotian Bai
 * @Github: https://github.com/164140757
 * @Date: 2020-05-24 15:54:00
 * @LastEditors: Haotian Bai
 * @LastEditTime: 2020-06-05 10:19:38
 * @FilePath: \Algorithms\src\test\java\TestModules\humanYeastTest\Human_YeastSub38NTest.java
 * @Description:
 */
<<<<<<< Updated upstream:src/test/java/Algorithms/Graph/HGA/Human_YeastSub38NTest.java
package Algorithms.Graph.HGA;

import Algorithms.Graph.Utils.AdjList.Graph;
import Algorithms.Graph.Utils.SimMat;
import IO.AbstractFileWriter;
import IO.GraphFileReader;
import IO.GraphFileReaderSpec;
import Tools.Stopwatch;
import org.jblas.DoubleMatrix;
=======
package Internal.Algorithms.Graph.HGA;

import Internal.Algorithms.DS.Network.AdjList.UndirectedGraph;
import Internal.Algorithms.DS.Network.SimMat;
import Internal.Algorithms.IO.GraphFileReader;
import Internal.Algorithms.IO.GraphFileReaderSpec;
import Internal.Algorithms.IO.GraphFileWriter;
import Internal.Algorithms.Tools.Stopwatch;
>>>>>>> Stashed changes:src/test/java/Internal/Algorithms/Graph/HGA/Human_YeastSub38NTest.java
import tech.tablesaw.api.*;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.tablesaw.plotly.traces.ScatterTrace;

@DisplayName("Reader is able to ")
public class Human_YeastSub38NTest extends GraphFileReaderSpec {
    Graph yeast;
    Graph human;
    SimMat simMat;
    HGA hga;
    Stopwatch stopwatch;
    private Vector<Integer> colIndexes;
    private Vector<Integer> rowIndexes;
    @BeforeEach
    void init() throws IOException {
        stopwatch = new Stopwatch();
        GraphFileReader reader = new GraphFileReader(true, true, false);
        yeast = reader.readToGraph("src/test/java/resources/TestModule/HGATestData/Human-YeastSub38N/net-38n.txt", false);
        human = reader.readToGraph("src/test/java/resources/TestModule/HGATestData/Human-YeastSub38N/HumanNet.txt", false);
        reader.setRecordNonZeros(true);
        reader.setRecordNeighbors(false);
        simMat = reader.readToSimMat("src/test/java/resources/TestModule/HGATestData/Human-YeastSub38N/fasta/yeastHumanSimList_EvalueLessThan1e-10.txt", yeast.getAllNodes(), human.getAllNodes(), true);
        hga = new HGA(simMat, yeast, human, 0.4,true,0.5,0.01);

    }


    @DisplayName("Topo")
    @Test
    void topo(){
        hga.addAllTopology();
    }

    @DisplayName("read simList from local blast result.")
    @Test
    void readSimilarityMatrixFromLocalBlast() throws IOException {
        Table table = Table.read().csv("src\\test\\java\\resources\\TestModule\\HGATestData\\Human-Yeast\\fasta\\result.csv");
        HashMap<String, Integer> map = new HashMap<>();
        // record max count
        table.stream().forEach(
                row -> {
                    String yeast = row.getString(0).split("\\u007C")[2].split("_YEAST")[0];
                    // count
                    if (!map.containsKey(yeast)) {
                        map.put(yeast, 1);
                    } else {
                        int num = map.get(yeast) + 1;
                        map.put(yeast, num);
                    }
//                String human = row.getString(1);
                }
        );
        // find max
        // hashmap to visualize
        StringColumn names = StringColumn.create("yeast_name");
        IntColumn counts = IntColumn.create("max_hit");
        int max = -Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry:map.entrySet()) {
            String k = entry.getKey();
            Integer v = entry.getValue();
            if(v>max){
                max = v;
            }
            names.append(k);
            counts.append(v);
        }
        IntColumn MAX = IntColumn.create("max");
        int total = map.size();
        while(total-->0) {
            MAX.append(max);
        }
        ScatterTrace lineTrace = ScatterTrace.builder(names, MAX)
                .mode(ScatterTrace.Mode.LINE)
                .name("max")
                .build();
        ScatterTrace scatterTrace = ScatterTrace.
                builder(names, counts)
                .mode(ScatterTrace.Mode.LINE)
                .name("count")
                .build();
        Axis xAxis = Axis.builder().title("yeast proteins").build();
        Axis yAxis = Axis.builder().title("hit counts").build();
        Layout layout = Layout.builder().title("E-value <= 10 filtering for yeast and human").xAxis(xAxis).yAxis(yAxis).build();
        Plot.show(new Figure(layout, scatterTrace,lineTrace));
        // yeast 6641; human 9588
    }


//    @DisplayName("HGA initialization.")
//    @Test
//    void PrepareHGATest() throws IOException {
//        String humanPath = "src/test/java/resources/TestModule/HGATestData/Human-YeastSub38N/HumanNet.txt";
//        String yeastPath = "src/test/java/resources/TestModule/HGATestData/Human-YeastSub38N/net-38n.txt";
//        yeast = readAdjList(yeastPath);// 38
//        human = readAdjList(humanPath);// 9141
//        simList = getReadySimList("src/test/java/resources/TestModule/HGATestData/Human-Yeast/fasta/yeastHumanSimList_EvalueLessThan1e-10.csv");
//        // simList 6612*9574(evalue <= 10)
//        // reassure it contains all nodes for two graphs
//        // yeast
//        assertTrue(simList.getUpdatedRowSet().containsAll(yeast.getAllNodes()));
//        assertTrue(simList.getColSet().containsAll(human.getAllNodes()));
//        System.out.println("yeast proteins number:" + simList.getRowSet().size() + "\nhuman proteins' number:" + simList.getColSet().size());
//        // save to disk
//        GraphFileWriter writer = new GraphFileWriter();
//        writer.writeToTxt(simList,"src/test/java/resources/TestModule/HGATestData/Human-Yeast/fasta/yeastHumanSimList_EvalueLessThan1e-10.txt");
//    }
//
//    @DisplayName("HGA mapping.")
//    @Test
//    void HGATest() throws IOException {
//        String humanPath = "src/test/java/resources/TestModule/HGATestData/Human-YeastSub38N/HumanNet.txt";
//        String yeastPath = "src/test/java/resources/TestModule/HGATestData/Human-YeastSub38N/net-38n.txt";
//        String simPath = "src\\test\\java\\resources\\TestModule\\HGATestData\\Human-Yeast\\fasta\\yeastHumanSimList_EvalueLessThan1e-10.txt";
//        yeast = readAdjList(yeastPath);// 38
//        human = readAdjList(humanPath);// 9141
//        simList = readAdjList(simPath);// 38*9141 1e-10
//        HGARun hgaRun = new HGARun(simList,yeast,human,true,0.4,0.01,5.);
//    }

    /**
     * Use after yeast and human networks have been initialized.
     * construct a simList with only nodes in yeast set and human set
     */
    SimMat getReadySimMat(String path) throws IOException {
        assert(human!=null);
        assert(yeast!=null);
        Table table = Table.read().csv(path);
        SimMat simMat = new SimMat(yeast.getAllNodes(),human.getAllNodes());
        table.stream().forEach(
                row -> {
                    String yeastNode = row.getString(0).split("\\u007C")[2].split("_YEAST")[0];
                    String humanNode = row.getString(1);
                    double evalue = 1/(1-1/Math.log(row.getDouble(2))) ;
                    if(human.getAllNodes().contains(humanNode) && yeast.getAllNodes().contains(yeastNode)){
                        simMat.put(yeastNode,humanNode,evalue);
                    }
                });
        return simMat;
    }
    public void outPutMatrix() throws FileNotFoundException {
        String debugOutputPath = "src\\test\\java\\TestModules\\humanYeastTest\\";
        AbstractFileWriter writer = new AbstractFileWriter() {
            @Override
            public void write(Vector<String> context, boolean closed) {
                super.write(context, true);
            }
        };
        Vector<String> matrixVec = new Vector<>();
        DoubleMatrix matrix = simMat.getMat();
        double[][] mat = matrix.toArray2();
        for (double[] doubles : mat) {
            for (int j = 0; j < mat[0].length; j++) {
                matrixVec.add(doubles[j] + " ");
            }
            matrixVec.add("\n");
        }
        writer.setPath(debugOutputPath + "matrix" + ".txt");
        writer.write(matrixVec, false);

    }

    @Test
    void parallel_1() {
        Stopwatch stopwatch = new Stopwatch();
        AtomicReference<Double> sum = new AtomicReference<>((double) 0);
        rowIndexes.parallelStream().forEach(r->colIndexes.parallelStream().forEach(c->{
            sum.updateAndGet(v -> v + simMat.getMat().get(r, c));
        }));
        stopwatch.outElapsedByMiniSecond();
        System.out.println(sum);
    }
    @Test
    void parallel_2() {
        Stopwatch stopwatch = new Stopwatch();
        AtomicReference<Double> sum = new AtomicReference<>((double) 0);
        rowIndexes.parallelStream().forEach(r->{
            colIndexes.forEach(j-> sum.updateAndGet(v->v+simMat.getMat().get(r,j)));
    });
        stopwatch.outElapsedByMiniSecond();
        System.out.println(sum);
    }


    @Test
    void noParallel(){
        Stopwatch stopwatch = new Stopwatch();
        double sum = 0;
        for (int i = 0; i < rowIndexes.size(); i++) {
            for (int j = 0; j < colIndexes.size(); j++) {
                sum += simMat.getMat().get(i,j);
            }
        }
        stopwatch.outElapsedByMiniSecond();
        System.out.println(sum);
    }

    @Test
    void run(){
        hga.run();
    }

    @Test
    void smallTest() {
        hga.addAllTopology();
    }
}

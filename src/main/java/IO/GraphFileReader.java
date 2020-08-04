package IO;

import DS.Network.UndirectedGraph;

import java.io.IOException;
import java.util.Vector;

import static Tools.Functions.isDouble;


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
@SuppressWarnings("unchecked")
public class GraphFileReader<V, E> extends AbstractFileReader {
    Class<V> vertexClass;
    Class<E> edgeClass;
    private UndirectedGraph<V, E> udG;

    public GraphFileReader(Class<V> vertexClass, Class<E> edgeClass) {
        this.vertexClass = vertexClass;
        this.edgeClass = edgeClass;
    }

    public UndirectedGraph<V, E> readToUndirectedGraph(String inputFilePath,boolean closeWhenFinished) throws IOException {
        // matches sequence of one or more whitespace characters.
        setInputFilePath(inputFilePath);
        udG = new UndirectedGraph<>(edgeClass);
        setSplitter("\\s+");
        Vector<V> sifLine = new Vector<>();
        String line;
        while ((line = reader.readLine()) != null) {
            Object[] tokens = splitter.split(line);
            if (tokens.length == 0) continue;
            //  it will be handled in pareLine()
            // which will throw an IOException if not the right case.
            for (Object token : tokens) {
                sifLine.add((V) token);
            }
            parseForGraph(udG, sifLine);
            // clean for each line
            sifLine.clear();
        }
        if (closeWhenFinished) {
            reader.close();
        }
        return udG;
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
     * @param graph   graph
     * @param sifLine result very line
     */
    private void parseForGraph(UndirectedGraph<V, E> graph, Vector<V> sifLine) throws IOException {

        int sifSize = sifLine.size();
        if (sifSize == 0) {
            throw new IOException("Nothing has been input!.");
        }
        if (sifSize == 2) {
            // node1 node1 val1 // a circle
            V src = sifLine.get(0);
            V tgt = sifLine.get(1);
            graph.addVertex(src);
            graph.addVertex(tgt);
            graph.addEdge(src, tgt);
        } else if ((sifSize - 1) % 2 != 0 || sifSize == 1) {
            throw new IOException("The file reader format is not correct.");
        } else {
            V src = sifLine.get(0);
            graph.addVertex(src);
            for (int index = 1; index < sifSize; index += 2) {
                // name
                V tgt = sifLine.get(index);
                String val = String.valueOf(sifLine.get(index + 1));
                graph.addVertex(tgt);
                graph.addEdge(src, tgt);
                if (isDouble(val)) {
                    graph.setEdgeWeight(src, tgt, Double.parseDouble(val));
                } else {
                    throw new IOException("The file reader format is not correct. Plus: some name-value pairs are incorrect!");
                }
            }
        }
    }

}

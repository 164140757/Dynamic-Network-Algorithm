/*
 * @Author: Haotian Bai
 * @Github: https://github.com/164140757
 * @Date: 2020-06-04 07:50:51
 * @LastEditors: Haotian Bai
 * @LastEditTime: 2020-06-05 08:46:55
 * @FilePath: \Algorithms\src\main\java\Algorithms\Alignment\Blast\BLASTLocal.java
 * @Description:  
 */ 
package Algorithms.Alignment.Blast;

import java.io.IOException;

import Algorithms.Graph.Network.AdjList;

public class BLASTLocal {
    public void createDB(String seqPath, String tgtPath,String logPath) throws IOException {
        StringBuffer command = new StringBuffer();
        command.append("cmd /c d: ");
        command.append(String.
        format("&& makeblastdb -in %s -dbtype prot -parse_seqids -out %s  -logfile %s",seqPath,tgtPath,logPath));
        Process process = Runtime.getRuntime().exec(command.toString());
    }
    public void blastp(String queryPath,String subjectPath)throws IOException{
        StringBuffer command = new StringBuffer();
        command.append("cmd /c d: ");
        command.append(String.format(blastp -subject %s -query %s -out result.out -outfmt \"6 qseqid sseqid evalue\" -num_threads 4", args) );
    }
}

/*
 * @Author: Haotian Bai
 * @Github: https://github.com/164140757
 * @Date: 2020-06-04 07:50:51
 * @LastEditors: Haotian Bai
 * @LastEditTime: 2020-06-04 09:37:10
 * @FilePath: \Algorithms\src\main\java\Algorithms\Alignment\Blast\BLASTLocal.java
 * @Description:  
 */ 
package Algorithms.Alignment.Blast;

import java.io.IOException;

import Algorithms.Graph.Network.AdjList;

public class BLASTLocal {
    public void createDB(String seqPath, String tgtPath) throws IOException {
        StringBuffer command = new StringBuffer();
        command.append("cmd /c d: ");
        command.append(String.
        format("&& makeblastdb -in %s -parse_seqids -out ",seqPath,tgtPath));
        Process process = Runtime.getRuntime().exec(command.toString());
    }
}

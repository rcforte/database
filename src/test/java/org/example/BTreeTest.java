package org.example;


import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

public class BTreeTest
{
    private final String fileName = "btree.bin";

    @Test
    public void get_returns_values_when_node_is_not_split()
        throws IOException
    {
        new File(fileName).delete();

        try (var file = new RandomAccessFile("btree.bin", "rw"))
        {
            var tree = new BTree(file);
            tree.put(1, "Rafael");
            tree.put(2, "Carmen");
            Assert.assertEquals("Rafael", tree.get(1));
            Assert.assertEquals("Carmen", tree.get(2));
        }
    }

    @Test
    public void get_returns_null_when_node_is_not_split()
        throws IOException
    {
        new File(fileName).delete();

        try (var file = new RandomAccessFile("btree.bin", "rw"))
        {
            var tree = new BTree(file);
            tree.put(1, "Rafael");
            tree.put(2, "Carmen");
            Assert.assertNull(tree.get(0));
            Assert.assertNull(tree.get(3));
        }
    }

    @Test
    public void getReturnsOnSplitNodes()
        throws IOException
    {
        new File(fileName).delete();

        try (var file = new RandomAccessFile("btree.bin", "rw"))
        {
            var names = List.of("Rafael", "Carmen", "Ricardo",
                "Milena", "Alvaro", "Debora", "John");

            var tree = new BTree(file);
            for (int i = 0; i < names.size(); i++)
            {
                tree.put(i + 1, names.get(i));
            }

            for (int i = 0, n = names.size(); i < n; i++)
            {
                Assert.assertEquals(names.get(i), tree.get(i + 1));
            }
        }
    }
}

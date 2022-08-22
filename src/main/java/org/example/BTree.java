package org.example;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class BTree
{
    static final class Node
    {
        private static final int PAGE_SIZE = 4 * 1024;
        private static final int MAX = 4;

        public static Node load(RandomAccessFile file, Long offset)
            throws IOException
        {
            file.seek(offset);

            var count = file.readInt();
            var node = new Node(count);

            for (int i = 0; i < count; i++)
            {
                node.entries[i] = Entry.load(file);
            }

            return node;
        }

        private final Entry[] entries = new Entry[MAX];
        private int count;
        private Long offset;

        public Node(int count)
        {
            this.count = count;
        }

        // TODO: make sure the node size is equal to page size and pre-allocate this space in the file.
        public long save(RandomAccessFile file)
            throws IOException
        {
            // Setup a array backed data output stream.
            var buffer = new ByteArrayOutputStream();
            var output = new DataOutputStream(buffer);

            // Save the node contents.
            output.writeInt(count);
            for (int i = 0; i < count; i++)
            {
                entries[i].save(output);
            }

            // Make the array page size.
            var array = new byte[PAGE_SIZE];
            var source = buffer.toByteArray();
            System.arraycopy(source, 0, array, 0, source.length);

            // Save the array to disk.
            if (offset == null)
            {
                offset = file.length();
            }
            file.seek(offset);
            file.write(array);

            return offset;
        }


        @Override
        public String toString()
        {
            return offset + ":" + Arrays.toString(entries);
        }
    }

    static final class Entry
    {
        public static Entry load(RandomAccessFile file)
            throws IOException
        {
            // Read key.
            var key = file.readInt();

            // Read value.
            var value = (String) null;
            var length = file.readInt();
            if (length > 0)
            {
                var array = new byte[length];
                file.read(array);
                value = new String(array);
            }

            // Read offset.
            var nextOffset = (Long) file.readLong();
            if (nextOffset == -1)
            {
                nextOffset = null;
            }

            // Return new Entry instance.
            return new Entry(key, value, nextOffset, null);
        }

        private final Integer key;
        private final String value;

        public Node next;
        public Long nextOffset;

        public Entry(Integer key, String value, Long nextOffset, Node next)
        {
            this.key = key;
            this.value = value;
            this.nextOffset = nextOffset;
            this.next = next;
        }

        public void save(DataOutputStream output)
            throws IOException
        {
            output.writeInt(key);

            if (value != null)
            {
                output.writeInt(value.getBytes().length);
                output.write(value.getBytes());
            }
            else
            {
                output.writeInt(0);
            }

            if (nextOffset != null)
            {
                output.writeLong(nextOffset);
            }
            else
            {
                output.writeLong(-1);
            }
        }

        public void loadNext(RandomAccessFile file)
            throws IOException
        {
            if (nextOffset == null)
            {
                throw new IllegalStateException("Cannot load next node, the referenced offset is null");
            }

            next = Node.load(file, nextOffset);
        }

        @Override
        public String toString()
        {
            return key + ":" + value + ":" + nextOffset;
        }
    }

    private final RandomAccessFile file;
    private Node root;
    private int height;
    private int count;

    public BTree(RandomAccessFile f)
        throws IOException
    {
        file = f;

        if (file.length() == 0)
        {
            file.writeInt(count);
            file.writeInt(height);

            root = new Node(0);
            root.save(file);
        }
        else
        {
            count = file.readInt();
            height = file.readInt();
            root = Node.load(file, file.getFilePointer());
        }
    }

    public void put(Integer key, String value)
        throws IOException
    {
        if (key == null)
        {
            throw new IllegalArgumentException("key cannot be null");
        }
        if (value == null)
        {
            throw new IllegalArgumentException("value cannot be null");
        }

        var newNode = insert(root, key, value, height);
        count++;

        if (newNode != null)
        {
            // Nodes resulting from a split will not be saved by
            // insert, we need to save them here.
            newNode.save(file);

            // Create the new root.
            var newRoot = new Node(2);
            newRoot.offset = file.length();
            newRoot.entries[0] = new Entry(root.entries[0].key, null, root.offset, root);
            newRoot.entries[1] = new Entry(newNode.entries[0].key, null, newNode.offset, newNode);

            // root becomes a child of newRoot
            newRoot.entries[0].nextOffset = newRoot.offset;

            // Swap root and newRoot offsets
            var tempOffset = root.offset;
            root.offset = newRoot.offset;
            newRoot.offset = tempOffset;

            // Save new nodes.
            newRoot.save(file);
            root.save(file);

            // Update the memory references.
            root = newRoot;
            height++;
        }

        // Save the btree.
        file.seek(0L);
        file.writeInt(count);
        file.writeInt(height);
    }

    private Node insert(Node node, Integer key, String value, int height)
        throws IOException
    {
        var insertIndex = 0;
        var newEntry = new Entry(key, value, null, null);

        if (height == 0)
        {
            for (insertIndex = 0;
                 insertIndex < node.count;
                 insertIndex++)
            {
                if (key < node.entries[insertIndex].key)
                {
                    break;
                }
            }
        }
        else
        {
            for (insertIndex = 0;
                 insertIndex < node.count;
                 insertIndex++)
            {
                if (insertIndex + 1 == node.count || key < node.entries[insertIndex + 1].key)
                {
                    if (node.entries[insertIndex].next == null)
                    {
                        node.entries[insertIndex].loadNext(file);
                    }

                    var newNode = insert(node.entries[insertIndex++].next, key, value, height - 1);
                    if (newNode == null)
                    {
                        return null;
                    }

                    newEntry = new Entry(newNode.entries[0].key, null, newNode.save(file), newNode);
                    break;
                }
            }
        }

        for (int i = node.count; i > insertIndex; i--)
        {
            node.entries[i] = node.entries[i - 1];
        }
        node.entries[insertIndex] = newEntry;
        node.count++;
        node.save(file);

        if (node.count == Node.MAX)
        {
            return split(node);
        }

        return null;
    }

    private Node split(Node originalNode)
    {
        final var half = Node.MAX / 2;
        final var newNode = new Node(half);

        for (var i = 0; i < half; i++)
        {
            newNode.entries[i] = originalNode.entries[half + i];
            originalNode.entries[half + i] = null;
        }
        originalNode.count = half;

        return newNode;
    }

    public String get(Integer key)
        throws IOException
    {
        return search(root, key, height);
    }

    private String search(Node node, Integer key, int height)
        throws IOException
    {
        if (height == 0)
        {
            for (int i = 0; i < node.count; i++)
            {
                if (key.intValue() == node.entries[i].key.intValue())
                {
                    return node.entries[i].value;
                }
            }
        }
        else
        {
            for (int i = 0; i < node.count; i++)
            {
                if (i + 1 == node.count || key < node.entries[i + 1].key)
                {
                    if (node.entries[i].next == null)
                    {
                        node.entries[i].loadNext(file);
                    }

                    return search(node.entries[i].next, key, height - 1);
                }
            }
        }

        return null;
    }
}


/**
 * Copyright (c) 2011-2012, Cornell University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of HyperDex nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/* Descriptions borrowed from YCSB base. */

package hyperclient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.*;

import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.StringByteIterator;

import hyperclient.*;

public class HyperClientYCSB extends DB
{
    private HyperClient m_client;
    private Pattern m_pat;
    private Matcher m_mat;
    private boolean m_scannable;
    private int m_retries;

    /**
     * Initialize any state for this DB.
     * Called once per DB instance; there is one DB instance per client thread.
     */
    public void init() throws DBException
    {
        String host = getProperties().getProperty("hyperclient.host", "127.0.0.1");
        Integer port = Integer.parseInt(getProperties().getProperty("hyperclient.port", "1234"));
        m_client = new HyperClient(host, port);
        m_pat = Pattern.compile("([a-zA-Z]*)([0-9]*)");
        m_mat = m_pat.matcher("user1");
        m_scannable = getProperties().getProperty("hyperclient.scannable", "false").equals("true");
        m_retries = 10;
    }

    /**
     * Cleanup any state for this DB.
     * Called once per DB instance; there is one DB instance per client thread.
     */
    public void cleanup() throws DBException
    {
    }

    /**
     * Read a record from the database. Each field/value pair from the result will be stored in a HashMap.
     *
     * @param table The name of the table
     * @param key The record key of the record to read.
     * @param fields The list of fields to read, or null for all of them
     * @param result A HashMap of field/value pairs for the result
     * @return Zero on success, a non-zero error code on error or "not found".
     */
    public int read(String table, String key, Set<String> fields, HashMap<String,ByteIterator> result)
    {
        Map map = new HashMap<String,Object>();

        try
        {
            map = m_client.get(table, key);
        }
        catch(Exception e)
        {
            return 1;
        }

        convert_to_java(fields, map, result);

        return 0;
    }

    /**
     * Perform a range scan for a set of records in the database. Each field/value pair from the result will be stored in a HashMap.
     *
     * @param table The name of the table
     * @param startkey The record key of the first record to read.
     * @param recordcount The number of records to read
     * @param fields The list of fields to read, or null for all of them
     * @param result A Vector of HashMaps, where each HashMap is a set field/value pairs for one record
     * @return Zero on success, a non-zero error code on error.  See this class's description for a discussion of error codes.
     */
    public int scan(String table, String startkey, int recordcount, Set<String> fields, Vector<HashMap<String,ByteIterator>> result)
    {
    /*
        // XXX I'm going to be lazy and not support "fields" for now.  Patches
        // welcome.
        if (!m_scannable)
        {
            return 1000;
        }

        m_mat.reset(startkey);

        if (!m_mat.matches())
        {
            return 1001;
        }

        long base = Long.parseLong(m_mat.group(2));
        long lower = base << 32;
        long upper = (base + recordcount) << 32;

        HyperVector res = new HyperVector();

        ReturnCode ret = m_client.range_search(table, "recno", lower, upper, res);

        for (int i = 0; i < m_retries && ret != ReturnCode.SUCCESS; ++i)
        {
            ret = m_client.range_search(table, "recno", lower, upper, res);
        }

        if (ret == ReturnCode.SUCCESS)
        {
            for (int i = 0; i < res.size(); ++i)
            {
                HyperMap e = HyperMap.dynamic_cast(res.get(i));
                HashMap<String, ByteIterator> e2 = new HashMap<String, ByteIterator>();
                convert_to_java(fields, e, e2);
                result.add(e2);
            }
            return 0;
        }

        return ret.swigValue();
    */
    return 0;
    }

    /**
     * Update a record in the database. Any field/value pairs in the specified values HashMap will be written into the record with the specified
     * record key, overwriting any existing values with the same field name.
     *
     * @param table The name of the table
     * @param key The record key of the record to write.
     * @param values A HashMap of field/value pairs to update in the record
     * @return Zero on success, a non-zero error code on error.  See this class's description for a discussion of error codes.
     */
    public int update(String table, String key, HashMap<String,ByteIterator> values)
    {
        Map<String,Object> res = new HashMap<String,Object>();
        convert_from_java(values, res);

        if (m_scannable)
        {
            m_mat.reset(key);

            if (!m_mat.matches())
            {
                return -1;
            }
  
            long num = Long.parseLong(m_mat.group(2));
            res.put("recno", new Long(num << 32));
        }

        try
        {
            Boolean ret = m_client.put(table, key, res);
            return ret.booleanValue()?0:1;
        }
        catch(Exception e)
        {
            return 1;
        }
    }

    /**
     * Insert a record in the database. Any field/value pairs in the specified values HashMap will be written into the record with the specified
     * record key.
     *
     * @param table The name of the table
     * @param key The record key of the record to insert.
     * @param values A HashMap of field/value pairs to insert in the record
     * @return Zero on success, a non-zero error code on error.  See this class's description for a discussion of error codes.
     */
    public int insert(String table, String key, HashMap<String,ByteIterator> values)
    {
        return update(table, key, values);
    }

    /**
     * Delete a record from the database.
     *
     * @param table The name of the table
     * @param key The record key of the record to delete.
     * @return Zero on success, a non-zero error code on error.  See this class's description for a discussion of error codes.
     */
    public int delete(String table, String key)
    {
        try
        {
            boolean ret = m_client.del(table, key);
            return ret?0:1;
        }
        catch(Exception e)
        {
            return 1;
        }
    }

    private void convert_to_java(Set<String> fields, Map interres, HashMap<String,ByteIterator> result)
    {
        if (fields == null)
        {
            return;
        }

        for (String key : fields)
        {
            // Q: under which condition, interres.containsKey(key) is false?
            if (interres.containsKey(key))
            {
                result.put(key, new StringByteIterator(interres.get(key).toString()));
            }
        }
    }

    private void convert_from_java(HashMap<String,ByteIterator> result, Map<String,Object> interres)
    {
        Map<String, ByteIterator> r = result;
        for (Map.Entry<String, ByteIterator> entry : r.entrySet())
        {
            interres.put(entry.getKey(), entry.getValue().toString());
        }
    }
}

/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.importers.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.importers.tree.TreeReader.Token;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

public class XmlImportUtilities extends TreeImportUtilities {
    final static Logger logger = LoggerFactory.getLogger("XmlImportUtilities");

    static public String[] detectPathFromTag(TreeReader parser, String tag) {
        try {
            while (parser.hasNext()) {
                Token eventType = parser.next();
                if (eventType == Token.StartEntity) {//XMLStreamConstants.START_ELEMENT) {
                    List<String> path = detectRecordElement(parser, tag);
                    if (path != null) {
                        String[] path2 = new String[path.size()];

                        path.toArray(path2);

                        return path2;
                    }
                }
            }
        } catch (Exception e) {
            // silent
            // e.printStackTrace();
        }

        return null;
    }

    /**
     * Looks for an element with the given tag name in the Tree data being parsed, returning the path hierarchy to reach it.
     *
     * @param parser
     * @param tag
     *         The element name (can be qualified) to search for
     * @return
     *         If the tag is found, an array of strings is returned.
     *         If the tag is at the top level, the tag will be the only item in the array.
     *         If the tag is nested beneath the top level, the array is filled with the hierarchy with the tag name at the last index
     *         null if the the tag is not found.
     * @throws ServletException
     */
    static protected List<String> detectRecordElement(TreeReader parser, String tag) throws Exception {
        try{
            if(parser.current() == Token.Ignorable) {
                parser.next();
            }

            String localName = parser.getFieldName();
            String fullName = composeName(parser.getPrefix(), localName);
            if (tag.equals(parser.getFieldName()) || tag.equals(fullName)) {
                List<String> path = new LinkedList<String>();
                path.add(localName);

                return path;
            }

            while (parser.hasNext()) {
                Token eventType = parser.next();
                if (eventType == Token.EndEntity) {//XMLStreamConstants.END_ELEMENT) {
                    break;
                } else if (eventType == Token.StartEntity) {//XMLStreamConstants.START_ELEMENT) {
                    List<String> path = detectRecordElement(parser, tag);
                    if (path != null) {
                        path.add(0, localName);
                        return path;
                    }
                }
            }
        } catch (Exception e) {
            // silent
            // e.printStackTrace();
        }
        return null;
    }

    static protected String composeName(String prefix, String localName) {
        return prefix != null && prefix.length() > 0 ? (prefix + ":" + localName) : localName;
    }

    /**
     * Seeks for recurring element in a parsed document
     * which are likely candidates for being data records
     * @param parser
     *              The parser loaded with tree data
     * @return
     *              The path to the most numerous of the possible candidates.
     *              null if no candidates were found (less than 6 recurrences)
     */
    static public String[] detectRecordElement(TreeReader parser) {
        logger.trace("detectRecordElement(inputStream)");
        List<RecordElementCandidate> candidates = new ArrayList<RecordElementCandidate>();

        try {
            while (parser.hasNext()) {
                Token eventType = parser.next();
                if (eventType == Token.StartEntity) {
                    RecordElementCandidate candidate =
                        detectRecordElement(
                            parser,
                            new String[] { parser.getFieldName() });

                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        } catch (Exception e) {
            // silent
            // e.printStackTrace();
        }

        if (candidates.size() > 0) {
            sortRecordElementCandidates(candidates);

            return candidates.get(0).path;
        }
        logger.info("No candidate elements were found in data - at least 6 similar elements are required");
        return null;
    }

    static protected RecordElementCandidate detectRecordElement(TreeReader parser, String[] path) {
        logger.trace("detectRecordElement(TreeReader, String[])");
        List<RecordElementCandidate> descendantCandidates = new ArrayList<RecordElementCandidate>();

        Map<String, Integer> immediateChildCandidateMap = new HashMap<String, Integer>();
        int textNodeCount = 0;
        int childElementNodeCount = 0;

        try {
            while (parser.hasNext()) {
                Token eventType = parser.next();
                if (eventType == Token.EndEntity ) {
                    break;
                } else if (eventType == Token.Value) {
                    try{
                        if (parser.getFieldValue().trim().length() > 0) {
                            textNodeCount++;
                        }
                    }catch(Exception e){
                        //silent
                    }
                } else if (eventType == Token.StartEntity) {
                    childElementNodeCount++;

                    String tagName = parser.getFieldName();

                    immediateChildCandidateMap.put(
                        tagName,
                        immediateChildCandidateMap.containsKey(tagName) ?
                                immediateChildCandidateMap.get(tagName) + 1 : 1);

                    String[] path2 = new String[path.length + 1];
                    System.arraycopy(path, 0, path2, 0, path.length);
                    path2[path.length] = tagName;

                    RecordElementCandidate c = detectRecordElement(parser, path2);
                    if (c != null) {
                        descendantCandidates.add(c);
                    }
                }
            }
        } catch (Exception e) {
            // silent
            // e.printStackTrace();
        }

        if (immediateChildCandidateMap.size() > 0) {
            List<RecordElementCandidate> immediateChildCandidates = new ArrayList<RecordElementCandidate>(immediateChildCandidateMap.size());
            for (Entry<String, Integer> entry : immediateChildCandidateMap.entrySet()) {
                int count = entry.getValue();
                if (count > 1) {
                    String[] path2 = new String[path.length + 1];
                    System.arraycopy(path, 0, path2, 0, path.length);
                    path2[path.length] = entry.getKey();

                    RecordElementCandidate candidate = new RecordElementCandidate();
                    candidate.path = path2;
                    candidate.count = count;
                    immediateChildCandidates.add(candidate);
                }
            }

            if (immediateChildCandidates.size() > 0 && immediateChildCandidates.size() < 5) {
                // There are some promising immediate child elements, but not many,
                // that can serve as record elements.

                sortRecordElementCandidates(immediateChildCandidates);

                RecordElementCandidate ourCandidate = immediateChildCandidates.get(0);
                logger.trace("ourCandidate.count : " + ourCandidate.count + "; immediateChildCandidates.size() : " + immediateChildCandidates.size());
                if (ourCandidate.count / immediateChildCandidates.size() > 5) {
                    return ourCandidate;
                }

                descendantCandidates.add(ourCandidate);
            }
        }

        if (descendantCandidates.size() > 0) {
            sortRecordElementCandidates(descendantCandidates);

            RecordElementCandidate candidate = descendantCandidates.get(0);
            if (candidate.count / descendantCandidates.size() > 5) {
                return candidate;
            }
        }

        return null;
    }



    static public void importTreeData(
        TreeReader parser,
        Project project,
        String[] recordPath,
        ImportColumnGroup rootColumnGroup,
        int limit
    ) {
        logger.trace("importTreeData(TreeReader, Project, String[], ImportColumnGroup)");
        try {
            while (parser.hasNext() && (limit <= 0 || project.rows.size() < limit)) {
                Token eventType = parser.next();
                if (eventType == Token.StartEntity) {
                    findRecord(project, parser, recordPath, 0, rootColumnGroup, limit);
                }
            }
        } catch (Exception e) {
            logger.error("Exception from XML parse",e);
        }
    }



    /**
     *
     * @param project
     * @param parser
     * @param recordPath
     * @param pathIndex
     * @param rootColumnGroup
     * @throws ServletException
     */
    static protected void findRecord(
        Project project,
        TreeReader parser,
        String[] recordPath,
        int pathIndex,
        ImportColumnGroup rootColumnGroup,
        int limit
    ) throws Exception {
        logger.trace("findRecord(Project, TreeReader, String[], int, ImportColumnGroup");
        
        if(parser.current() == Token.Ignorable){//XMLStreamConstants.START_DOCUMENT){
            logger.warn("Cannot use findRecord method for START_DOCUMENT event");
            return;
        }
        
        String recordPathSegment = recordPath[pathIndex];
        
        String localName = parser.getFieldName();
        String fullName = composeName(parser.getPrefix(), localName);
        if (recordPathSegment.equals(localName) || recordPathSegment.equals(fullName)) {
            if (pathIndex < recordPath.length - 1) {
                while (parser.hasNext() && (limit <= 0 || project.rows.size() < limit)) {
                    Token eventType = parser.next();
                    if (eventType == Token.StartEntity) {
                        findRecord(project, parser, recordPath, pathIndex + 1, rootColumnGroup, limit);
                    } else if (eventType == Token.EndEntity ) {
                        break;
                    }
                }
            } else {
                processRecord(project, parser, rootColumnGroup);
            }
        } else {
            skip(parser);
        }
    }

    static protected void skip(TreeReader parser) throws Exception {
        while (parser.hasNext()) {
            Token eventType = parser.next();
            if (eventType == Token.StartEntity) {//XMLStreamConstants.START_ELEMENT) {
                skip(parser);
            } else if (eventType == Token.EndEntity) { //XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    /**
     * processRecord parses Tree data for a single element and it's sub-elements,
     * adding the parsed data as a row to the project
     * @param project
     * @param parser
     * @param rootColumnGroup
     * @throws ServletException
     */
    static protected void processRecord(
        Project project,
        TreeReader parser,
        ImportColumnGroup rootColumnGroup
    ) throws Exception {
        logger.trace("processRecord(Project,TreeReader,ImportColumnGroup)");
        ImportRecord record = new ImportRecord();

        processSubRecord(project, parser, rootColumnGroup, record);

        if (record.rows.size() > 0) {
            for (List<Cell> row : record.rows) {
                Row realRow = new Row(row.size());
                int cellCount = 0;

                for (int c = 0; c < row.size(); c++) {
                    Cell cell = row.get(c);
                    if (cell != null) {
                        realRow.setCell(c, cell);
                        cellCount++;
                    }
                }

                if (cellCount > 0) {
                    project.rows.add(realRow);
                }
            }
        }
    }

    /**
     *
     * @param project
     * @param parser
     * @param columnGroup
     * @param record
     * @throws ServletException
     */
    static protected void processSubRecord(
        Project project,
        TreeReader parser,
        ImportColumnGroup columnGroup,
        ImportRecord record
    ) throws Exception {
        logger.trace("processSubRecord(Project,TreeReader,ImportColumnGroup,ImportRecord)");
        
        if(parser.current() == Token.Ignorable) {
            return;
        }
        
        ImportColumnGroup thisColumnGroup = getColumnGroup(
                    project,
                    columnGroup,
                    composeName(parser.getPrefix(), parser.getFieldName()));
        
        thisColumnGroup.nextRowIndex = Math.max(thisColumnGroup.nextRowIndex, columnGroup.nextRowIndex);
        
        int attributeCount = parser.getAttributeCount();
        for (int i = 0; i < attributeCount; i++) {
            String text = parser.getAttributeValue(i).trim();
            if (text.length() > 0) {
                addCell(
                    project,
                    thisColumnGroup,
                    record,
                    composeName(parser.getAttributePrefix(i), parser.getAttributeLocalName(i)),
                    text
                );
            }
        }

        while (parser.hasNext()) {
            Token eventType = parser.next();
            if (eventType == Token.StartEntity) {
                processSubRecord(
                    project,
                    parser,
                    thisColumnGroup,
                    record
                );
            } else if (//eventType == XMLStreamConstants.CDATA ||
                        eventType == Token.Value) { //XMLStreamConstants.CHARACTERS) {
                String text = parser.getFieldValue();
                String colName = parser.getFieldName();
                if(text != null){
                    text = text.trim();
                    if (text.length() > 0) {
                        addCell(
                                project,
                                thisColumnGroup,
                                record,
                                colName,
                                text
                        );
                    }
                }
            } else if (eventType == Token.EndEntity) {
                break;
            }
        }

        int nextRowIndex = thisColumnGroup.nextRowIndex;
        for (ImportColumn column2 : thisColumnGroup.columns.values()) {
            nextRowIndex = Math.max(nextRowIndex, column2.nextRowIndex);
        }
        for (ImportColumnGroup columnGroup2 : thisColumnGroup.subgroups.values()) {
            nextRowIndex = Math.max(nextRowIndex, columnGroup2.nextRowIndex);
        }
        thisColumnGroup.nextRowIndex = nextRowIndex;
    }
}

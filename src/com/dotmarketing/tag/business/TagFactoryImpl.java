package com.dotmarketing.tag.business;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.common.util.SQLUtil;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.tag.model.Tag;
import com.dotmarketing.tag.model.TagInode;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

import java.util.*;

/**
 * @author Jonathan Gamba
 *         Date: 1/28/16
 */
public class TagFactoryImpl implements TagFactory {

    private static final String TAG_COLUMN_TAG_ID = "tag_id";
    private static final String TAG_COLUMN_TAGNAME = "tagname";
    private static final String TAG_COLUMN_HOST_ID = "host_id";
    private static final String TAG_COLUMN_USER_ID = "user_id";
    private static final String TAG_COLUMN_PERSONA = "persona";
    private static final String TAG_COLUMN_MOD_DATE = "mod_date";

    private static final String TAG_INODE_COLUMN_TAG_ID = "tag_id";
    private static final String TAG_INODE_COLUMN_INODE = "inode";
    private static final String TAG_INODE_COLUMN_FIELD_VAR_NAME = "field_var_name";
    private static final String TAG_INODE_COLUMN_MOD_DATE = "mod_date";

    private TagCache tagCache;
    private TagInodeCache tagInodeCache;

    public TagFactoryImpl () {
        tagCache = CacheLocator.getTagCache();
        tagInodeCache = CacheLocator.getTagInodeCache();
    }

    /**
	 * Get a list of all the tags created
	 * @return list of all tags created
	 * @throws DotDataException
	 */
    public List<Tag> getAllTags () throws DotDataException {

        //Execute the search
        final DotConnect dc = new DotConnect();
        dc.setSQL("SELECT * FROM tag");

        List<Tag> tags = convertForTags(dc.loadObjectResults());

        //And add the results to the cache
        for ( Tag tag : tags ) {
            if ( tagCache.get(tag.getTagId()) == null ) {
                tagCache.put(tag);
            }
        }

        return tags;
    }

    /**
     * Gets all the tags matched by name
     * @param name tag name
     * @return list of tags
     * @throws DotDataException
     */
    public List<Tag> getTagsByName ( String name ) throws DotDataException {

        List<Tag> tags = tagCache.getByName(name);
        if ( tags == null ) {

            name = escapeSingleQuote(name);

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT * FROM tag WHERE tagname = ?");
            dc.addParam(name.toLowerCase());

            tags = convertForTags(dc.loadObjectResults());

            //And add the results to the cache
            for ( Tag tag : tags ) {
                if ( tagCache.get(tag.getTagId()) == null ) {
                    tagCache.put(tag);
                }
            }
            tagCache.putForName(name, tags);
        }

        return tags;
    }

    /**
     * Gets all the tags matched by hostId 
     * @param hostId Host Id
     * @return list of tags
     * @throws DotDataException
     */
    public List<Tag> getTagsByHost ( String hostId ) throws DotDataException {

        List<Tag> tags = tagCache.getByHost(hostId);
        if ( tags == null ) {

            hostId = escapeSingleQuote(hostId);

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT * FROM tag WHERE host_id = ?");
            dc.addParam(hostId);

            tags = convertForTags(dc.loadObjectResults());

            //And add the results to the cache
            for ( Tag tag : tags ) {
                if ( tagCache.get(tag.getTagId()) == null ) {
                    tagCache.put(tag);
                }
            }
            tagCache.putForHost(hostId, tags);
        }

        return tags;
    }

    /**
     * Gets all the tags containing the tag name specified and the host Identifier is the specified hostId or the system host id
     * @param name  Tag name
     * @param hostId Host id
     * @return list of tags
     * @throws DotDataException
     */
    public List<Tag> getTagsLikeNameAndHostIncludingSystemHost ( String name, String hostId ) throws DotDataException {

        name = escapeSingleQuote(name);

        //Execute the search
        final DotConnect dc = new DotConnect();
        dc.setSQL("SELECT * FROM tag WHERE tagname LIKE ? AND (host_id LIKE ? OR host_id LIKE ?)");
        dc.addParam(name.toLowerCase() + "%");
        dc.addParam(hostId);
        dc.addParam(Host.SYSTEM_HOST);

        List<Tag> tags = convertForTags(dc.loadObjectResults());

        //And add the results to the cache
        for ( Tag tag : tags ) {
            if ( tagCache.get(tag.getTagId()) == null ) {
                tagCache.put(tag);
            }
        }

        return tags;
    }

    /**
     * Get all the tags matched by name and hostId
     * @param name  Tag name
     * @param hostId Host id
     * @return list of tags
     * @throws DotDataException
     */
    public Tag getTagByNameAndHost ( String name, String hostId ) throws DotDataException {

        Tag tag = tagCache.get(name, hostId);
        if ( tag == null ) {

            name = escapeSingleQuote(name);

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT * FROM tag WHERE tagname = ? AND host_id = ?");
            dc.addParam(name.toLowerCase());
            dc.addParam(hostId);

            List<Map<String, Object>> sqlResults = dc.loadObjectResults();
            if ( sqlResults != null && !sqlResults.isEmpty() ) {
                tag = convertForTag(sqlResults.get(0));
            }

            //And add the result to the cache
            if ( tag != null && tag.getTagId() != null ) {
                tagCache.put(tag);
            }
        }

        return tag;
    }

    /**
     * Gets a Tag by a tagId
     * @param tagId Tag identifer
     * @return a tag
     * @throws DotDataException
     */
    public Tag getTagByTagId ( String tagId ) throws DotDataException {

        Tag tag = tagCache.get(tagId);
        if ( tag == null ) {

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT * FROM tag WHERE tag_id = ?");
            dc.addParam(tagId);

            List<Map<String, Object>> sqlResults = dc.loadObjectResults();
            if ( sqlResults != null && !sqlResults.isEmpty() ) {
                tag = convertForTag(sqlResults.get(0));
            }

            //And add the result to the cache
            if ( tag != null && tag.getTagId() != null ) {
                tagCache.put(tag);
            }
        }

        return tag;
    }

    /**
     * Gets all the tags associated to a user by the userproxy inode
     * @param userInode UserProxy Inode
     * @return a list of all the tags associated to a user
     * @throws DotDataException
     */
    public List<Tag> getTagsForUserByUserInode ( String userInode ) throws DotDataException {
        return getTagsByInode(userInode);
    }

    /**
     * Gets a subset of all tags filtered by tag name and/or host name
     * @param tagName Tag name
     * @param hostFilter Host identifier
     * @param globalTagsFilter Is a global tag filter
     * @param excludePersonas True if Persona Tags should be exclude from the returning results
     * @param sort Tag field to order the results
     * @param start first record to get
     * @param count max amount of records to get
     * @return  a list of tags filtered by tag name or host name
     */
    public List<Tag> getFilteredTags(String tagName, String hostFilter, boolean globalTagsFilter, boolean excludePersonas, String sort, int start, int count) {
        try {

            sort = SQLUtil.sanitizeSortBy(sort);

            //Execute the update
            final DotConnect dc = new DotConnect();

            Host host = null;
            try {
                host = APILocator.getHostAPI().find(hostFilter, APILocator.getUserAPI().getSystemUser(), true);
            } catch ( Exception e ) {
                Logger.warn(Tag.class, "Unable to get host according to search criteria - hostId = " + hostFilter);
            }
            if ( UtilMethods.isSet(host) ) {

                String sortStr = "";
                if ( UtilMethods.isSet(sort) ) {
                    String sortDirection = sort.startsWith("-") ? "desc" : "asc";
                    sort = sort.startsWith("-") ? sort.substring(1, sort.length()) : sort;

                    if ( sort.equalsIgnoreCase("hostname") ) sort = "host_id";

                    sortStr = " order by " + sort + " " + sortDirection;
                } else {
                    sortStr = "order by tagname";
                }

                //Filter by tagname, hosts and persona
                if ( UtilMethods.isSet(tagName) ) {

                    String personaFragment = "";
                    if ( excludePersonas ) {
                        personaFragment = " AND persona = ? ";
                    }
                    String globalTagsFragment = "";
                    if ( globalTagsFilter ) {
                        globalTagsFragment = " AND (host_id = ? OR host_id = ?) ";
                    } else {
                        globalTagsFragment = " AND host_id = ? ";
                    }

                    String sql = "SELECT * FROM tag WHERE tagname LIKE ? " + globalTagsFragment + personaFragment + sortStr;

                    dc.setSQL(SQLUtil.addLimits(sql, start, count));
                    dc.addParam("%" + tagName.toLowerCase() + "%");
                    try {
                        dc.addParam(host.getMap().get("tagStorage").toString());
                    } catch ( NullPointerException e ) {
                        dc.addParam(Host.SYSTEM_HOST);
                    }
                    if ( globalTagsFilter ) {
                        dc.addParam(Host.SYSTEM_HOST);
                    }
                    if ( excludePersonas ) {
                        dc.addParam(false);
                    }
                } else if ( !UtilMethods.isSet(tagName) && globalTagsFilter ) {
                    //check if tag name is not set and if should display global tags
                    //it will check all global tags and current host tags.

                    String personaFragment = "";
                    if ( excludePersonas ) {
                        personaFragment = " AND persona = ? ";
                    }

                    String sql = "SELECT * FROM tag WHERE (host_id = ? OR host_id = ? ) " + personaFragment + sortStr;
                    dc.setSQL(SQLUtil.addLimits(sql, start, count));

                    try {
                        dc.addParam(host.getMap().get("tagStorage").toString());
                    } catch ( NullPointerException e ) {
                        dc.addParam(Host.SYSTEM_HOST);
                    }
                    dc.addParam(Host.SYSTEM_HOST);
                    if ( excludePersonas ) {
                        dc.addParam(false);
                    }
                } else {
                    //check all current host tags.
                    String sql = "SELECT * FROM tag ";

                    Object tagStorage = host.getMap().get("tagStorage");

                    if ( UtilMethods.isSet(tagStorage) ) {

                        String personaFragment = "";
                        if ( excludePersonas ) {
                            personaFragment = " AND persona = ? ";
                        }

                        sql = sql + "WHERE host_id = ? " + personaFragment + sortStr;
                        dc.setSQL(SQLUtil.addLimits(sql,start,count));
                        dc.addParam(tagStorage.toString());
                        if ( excludePersonas ) {
                            dc.addParam(false);
                        }
                    } else {
                        if ( excludePersonas ) {
                            sql = sql + "WHERE persona = ? " + sortStr;
                        }
                        dc.setSQL(sql);
                        if ( excludePersonas ) {
                            dc.addParam(false);
                        }
                    }
                }

                //Execute and return the result of the query
                return convertForTags(dc.loadObjectResults());
            }
        } catch ( Exception e ) {
            Logger.warn(Tag.class, "getFilteredTags failed:" + e, e);
        }
        return new java.util.ArrayList<>();
    }

    /**
     * Update the specified tagInode related to a tag
     * @param tagInode Tag inode
     * @param tagId Tag id
     * @throws DotDataException
     */
    public void updateTagInode ( TagInode tagInode, String tagId ) throws DotDataException {

        //First lets clean up the cache
        List<TagInode> cachedTagInodes = tagInodeCache.getByTagId(tagInode.getTagId());
        if ( cachedTagInodes != null && !cachedTagInodes.isEmpty() ) {
            for ( TagInode cachedTagInode : cachedTagInodes ) {
                tagCache.removeByInode(cachedTagInode.getInode());
            }
        }
        tagInodeCache.remove(tagInode);

        //Execute the update
        final DotConnect dc = new DotConnect();
        dc.setSQL("UPDATE tag_inode SET tag_id = ?, mod_date = ? WHERE tag_id = ? AND inode = ? AND field_var_name = ?");
        dc.addParam(tagId);
        dc.addParam(new Date());
        dc.addParam(tagInode.getTagId());
        dc.addParam(tagInode.getInode());
        dc.addParam(tagInode.getFieldVarName());

        dc.loadResult();
    }

    /**
     * Creates a new tag
     *
     * @param tag Tag to insert
     * @return Created tag
     * @throws DotDataException
     */
    public Tag createTag(Tag tag) throws DotDataException {

        if ( !UtilMethods.isSet(tag.getTagId()) ) {
            tag.setTagId(UUID.randomUUID().toString());
        }

        //Execute the insert
        final DotConnect dc = new DotConnect();
        dc.setSQL("INSERT INTO tag (tag_id, tagname, host_id, user_id, persona, mod_date) VALUES (?,?,?,?,?,?)");
        dc.addParam(tag.getTagId());
        dc.addParam(tag.getTagName());
        dc.addParam(tag.getHostId());
        dc.addParam(tag.getUserId());
        dc.addParam(tag.isPersona());
        dc.addParam(new Date());

        dc.loadResult();

        return tag;
    }

    /**
     * Create a new TagInode
     * @param tagInode TagInode to create
     * @return new TagInode created
     * @throws DotDataException
     */
    public TagInode createTagInode ( TagInode tagInode ) throws DotDataException {

        //First lets clean up the cache
        tagCache.removeByInode(tagInode.getInode());
        tagInodeCache.remove(tagInode);

        //Execute the insert
        final DotConnect dc = new DotConnect();
        dc.setSQL("INSERT INTO tag_inode (tag_id, inode, field_var_name, mod_date) VALUES (?,?,?,?)");
        dc.addParam(tagInode.getTagId());
        dc.addParam(tagInode.getInode());
        dc.addParam(tagInode.getFieldVarName());
        dc.addParam(new Date());

        dc.loadResult();

        return tagInode;
    }

    /**
     * Update a tag object by tagId
     * @param tag Tag object to update
     * @throws DotDataException
     */
    public void updateTag ( Tag tag ) throws DotDataException {

        //First lets clean up the cache
        List<TagInode> cachedTagInodes = tagInodeCache.getByTagId(tag.getTagId());
        if ( cachedTagInodes != null && !cachedTagInodes.isEmpty() ) {
            for ( TagInode cachedTagInode : cachedTagInodes ) {
                tagCache.removeByInode(cachedTagInode.getInode());
            }
        }
        tagCache.remove(tag);

        //Execute the update
        final DotConnect dc = new DotConnect();
        dc.setSQL("UPDATE tag SET tagname = ?, host_id = ?, user_id = ?, persona = ?, mod_date = ? WHERE tag_id = ?");
        dc.addParam(tag.getTagName());
        dc.addParam(tag.getHostId());
        dc.addParam(tag.getUserId());
        dc.addParam(tag.isPersona());
        dc.addParam(tag.getModDate());
        dc.addParam(tag.getTagId());

        dc.loadResult();
    }

    /**
     * Deletes a tag
     * @param tag tag to be deleted
     * @throws DotDataException
     */
    public void deleteTag ( Tag tag ) throws DotDataException {

        //First lets clean up the cache
        List<TagInode> cachedTagInodes = tagInodeCache.getByTagId(tag.getTagId());
        if ( cachedTagInodes != null && !cachedTagInodes.isEmpty() ) {
            for ( TagInode cachedTagInode : cachedTagInodes ) {
                tagCache.removeByInode(cachedTagInode.getInode());
            }
        }
        tagCache.remove(tag);
        tagInodeCache.removeByTagId(tag.getTagId());

        //Execute the update
        final DotConnect dc = new DotConnect();
        dc.setSQL("DELETE FROM tag WHERE tag_id = ?");
        dc.addParam(tag.getTagId());

        dc.loadResult();
    }

    /**
     * Gets all TagInodes associated to an object
     * @param inode inode of the object tagged
     * @return list of all the TagInode where the tags are associated to the object
     * @throws DotDataException
     */
    public List<TagInode> getTagInodesByInode ( String inode ) throws DotDataException {

        List<TagInode> tagInodes = tagInodeCache.getByInode(inode);
        if ( tagInodes == null ) {

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT * FROM tag_inode WHERE inode = ?");
            dc.addParam(inode);

            tagInodes = convertForTagInodes(dc.loadObjectResults());

            //And add the results to the cache
            for ( TagInode tagInode : tagInodes ) {
                if ( tagInodeCache.get(tagInode.getTagId(), tagInode.getInode(), tagInode.getFieldVarName()) == null ) {
                    tagInodeCache.put(tagInode);
                }
            }
            tagInodeCache.putForInode(inode, tagInodes);
        }

        return tagInodes;
    }

    /**
     * Gets all TagInodes associated to a tag
     * @param tagId tagId of the object tagged
     * @return list of all the TagInode where the tags are associated to the object
     * @throws DotDataException
     */
    public List<TagInode> getTagInodesByTagId ( String tagId ) throws DotDataException {

        List<TagInode> tagInodes = tagInodeCache.getByTagId(tagId);
        if ( tagInodes == null ) {

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT * FROM tag_inode where tag_id = ?");
            dc.addParam(tagId);

            tagInodes = convertForTagInodes(dc.loadObjectResults());

            //And add the results to the cache
            for ( TagInode tagInode : tagInodes ) {
                if ( tagInodeCache.get(tagInode.getTagId(), tagInode.getInode(), tagInode.getFieldVarName()) == null ) {
                    tagInodeCache.put(tagInode);
                }
            }
            tagInodeCache.putForTagId(tagId, tagInodes);
        }

        return tagInodes;
    }

    /**
     * Gets a tagInode by name and inode
     * @param tagId id of the tag
     * @param inode inode of the object tagged
     * @param fieldVarName varname of the tag field
     * @return the tagInode
     * @throws DotDataException
     */
    public TagInode getTagInode ( String tagId, String inode, String fieldVarName ) throws DotDataException {

        TagInode tagInode = tagInodeCache.get(tagId, inode, fieldVarName);
        if ( tagInode == null ) {

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT * FROM tag_inode WHERE tag_id = ? AND inode = ? AND field_var_name = ?");
            dc.addParam(tagId);
            dc.addParam(inode);
            dc.addParam(fieldVarName);

            List<Map<String, Object>> sqlResults = dc.loadObjectResults();
            if ( sqlResults != null && !sqlResults.isEmpty() ) {
                tagInode = convertForTagInode(sqlResults.get(0));
            }

            //And add the result to the cache
            if ( tagInode != null && tagInode.getTagId() != null ) {
                tagInodeCache.put(tagInode);
            }
        }

        return tagInode;
    }

    /**
     * Deletes TagInodes references by inode
     * @param inode inode reference to delete
     * @throws DotDataException
     */
    public void deleteTagInodesByInode(String inode) throws DotDataException {

        //First lets clean up the cache
        tagInodeCache.removeByInode(inode);
        tagCache.removeByInode(inode);

        //Execute the update
        final DotConnect dc = new DotConnect();
        dc.setSQL("DELETE FROM tag_inode WHERE inode = ?");
        dc.addParam(inode);

        dc.loadResult();
    }

    /**
     * Deletes a TagInode
     * @param tagInode TagInode to delete
     * @throws DotDataException
     */
    public void deleteTagInode ( TagInode tagInode ) throws DotDataException {

        //First lets clean up the cache
        tagInodeCache.remove(tagInode);
        tagCache.removeByInode(tagInode.getInode());

        //Execute the update
        final DotConnect dc = new DotConnect();
        dc.setSQL("DELETE FROM tag_inode WHERE tag_id = ? AND inode = ? AND field_var_name = ?");
        dc.addParam(tagInode.getTagId());
        dc.addParam(tagInode.getInode());
        dc.addParam(tagInode.getFieldVarName());

        dc.loadResult();
    }

    /**
     * Gets all the tags associated to an object
     * @param inode Inode of the tagged object
     * @return list of all the tags associated to an object
     * @throws DotDataException
     */
    @Override
    public List<Tag> getTagsByInode ( String inode ) throws DotDataException {

        List<Tag> tags = tagCache.getByInode(inode);
        if ( tags == null ) {

            //Execute the search
            final DotConnect dc = new DotConnect();
            dc.setSQL("SELECT tag.* FROM tag_inode tagInode, tag tag WHERE tagInode.tag_id=tag.tag_id AND tagInode.inode = ?");
            dc.addParam(inode);

            tags = convertForTags(dc.loadObjectResults());

            //And add the results to the cache
            for ( Tag tag : tags ) {
                if ( tagCache.get(tag.getTagId()) == null ) {
                    tagCache.put(tag);
                }
            }
            tagCache.putForInode(inode, tags);
        }

        return tags;
    }

    /**
     * Escape a single quote
     *
     * @param tagName string with single quotes
     * @return single quote string escaped
     */
    private String escapeSingleQuote ( String tagName ) {
        return tagName.replace("'", "''");
    }

    /**
     * Convert the SQL tags results into a list of Tags objects
     * @param sqlResults sql query results
     * @return a list of tags objects
     */
    private List<Tag> convertForTags ( List<Map<String, Object>> sqlResults ) {

        List<Tag> tags = new ArrayList<>();

        if ( sqlResults != null ) {

            for ( Map<String, Object> row : sqlResults ) {
                Tag tag = convertForTag(row);
                tags.add(tag);
            }
        }

        return tags;
    }

    /**
     * Convert the SQL tagInodes results into a list of TagInodes objects
     * @param sqlResults sql query results
     * @return a list of tags objects
     */
    private List<TagInode> convertForTagInodes ( List<Map<String, Object>> sqlResults ) {

        List<TagInode> tagInodes = new ArrayList<>();

        if ( sqlResults != null ) {

            for ( Map<String, Object> row : sqlResults ) {
                TagInode tagInode = convertForTagInode(row);
                tagInodes.add(tagInode);
            }
        }

        return tagInodes;
    }

    /**
     * Convert the SQL tag result into a Tag object
     * @param sqlResults sql query result
     * @return a Tag object
     */
    private Tag convertForTag ( Map<String, Object> sqlResult ) {

        Tag tag = null;
        if ( sqlResult != null ) {
            tag = new Tag();
            tag.setTagId((String) sqlResult.get(TAG_COLUMN_TAG_ID));
            tag.setTagName((String) sqlResult.get(TAG_COLUMN_TAGNAME));
            tag.setHostId((String) sqlResult.get(TAG_COLUMN_HOST_ID));
            tag.setUserId((String) sqlResult.get(TAG_COLUMN_USER_ID));
            if ( DbConnectionFactory.isMsSql() || DbConnectionFactory.isOracle() ) {
                tag.setPersona(Boolean.valueOf(sqlResult.get(TAG_COLUMN_PERSONA).toString()));
            } else {
                tag.setPersona((boolean) sqlResult.get(TAG_COLUMN_PERSONA));
            }
            tag.setModDate((Date) sqlResult.get(TAG_COLUMN_MOD_DATE));
        }

        return tag;
    }

    /**
     * Convert the SQL tagInode result into a TagInode object
     * @param sqlResults sql query result
     * @return a TagInode object
     */
    private TagInode convertForTagInode ( Map<String, Object> sqlResult ) {

        TagInode tagInode = null;
        if ( sqlResult != null ) {
            tagInode = new TagInode();
            tagInode.setTagId((String) sqlResult.get(TAG_INODE_COLUMN_TAG_ID));
            tagInode.setInode((String) sqlResult.get(TAG_INODE_COLUMN_INODE));
            tagInode.setFieldVarName((String) sqlResult.get(TAG_INODE_COLUMN_FIELD_VAR_NAME));
            tagInode.setModDate((Date) sqlResult.get(TAG_INODE_COLUMN_MOD_DATE));
        }

        return tagInode;
    }

}
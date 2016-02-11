package com.dotmarketing.tag.business;

import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.UserProxy;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.FactoryLocator;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.tag.model.Tag;
import com.dotmarketing.tag.model.TagInode;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

import java.util.*;

public class TagAPIImpl implements TagAPI {

    private TagFactory tagFactory = FactoryLocator.getTagFactory();

    /**
	 * Get a list of all the tags created
	 * @return list of all tags created
	 */
    public java.util.List<Tag> getAllTags () throws DotDataException {
        return tagFactory.getAllTags();
    }

    /**
	 * Gets a Tag by name
	 * @param name name of the tag to get
	 * @return tag
	 */
    public java.util.List<Tag> getTagsByName ( String name ) throws DotDataException {
        return tagFactory.getTagsByName(name);
    }

    /**
	 * Get the list of tags related to a user by the user Id
	 * @param userId User id
	 * @return List<Tag>
	 * @throws DotDataException
	 * @throws DotSecurityException
	 */
    public java.util.List<Tag> getTagsForUserByUserId ( String userId ) throws DotDataException, DotSecurityException {

        //First lets seach for the user
        UserProxy user = APILocator.getUserProxyAPI().getUserProxy(userId, APILocator.getUserAPI().getSystemUser(), false);

        //And return the tags related to the user
        return getTagsForUserByUserInode(user.getInode());
    }

    /**
	 * Get the list of tags by the users TagInode inode
	 * @param userInode Users TagInode inode
	 * @return List<Tag>
     * @throws DotDataException
     */
    public java.util.List<Tag> getTagsForUserByUserInode ( String userInode ) throws DotDataException {
        return tagFactory.getTagsForUserByUserInode(userInode);
    }

    /**
	 * Gets all tags filtered by tag name and/or host name paginated. <strong>This method excludes Persona Tags by default.</strong>
	 * @param tagName tag name
	 * @param hostFilter host name
	 * @param globalTagsFilter 
	 * @param sort Tag field to order
	 * @param start first entry to get
	 * @param count max amount of entries to show
	 * @return List<Tag>
	 */
    public java.util.List<Tag> getFilteredTags ( String tagName, String hostFilter, boolean globalTagsFilter, String sort, int start, int count ) {
        return tagFactory.getFilteredTags(tagName, hostFilter, globalTagsFilter, true, sort, start, count);
    }

    /**
	 * Gets a Tag by name, validates the existance of the tag, if it doesn't exists then is created
	 * @param name name of the tag to get
	 * @param hostId host identifier
	 * @return Tag
	 * @throws Exception
	 */
    public Tag getTagAndCreate ( String name, String hostId ) throws DotDataException, DotSecurityException {
        return getTagAndCreate(name, "", hostId, false);
    }

    /**
     * Gets a Tag by name, validates the existance of the tag, if it doesn't exists then is created
     *
     * @param name   name of the tag to get
     * @param userId owner of the tag
     * @param hostId host identifier
     * @return Tag
     * @throws Exception
     */
    public Tag getTagAndCreate ( String name, String userId, String hostId ) throws DotDataException, DotSecurityException {
        return getTagAndCreate(name, userId, hostId, false);
    }

    /**
     * Gets a Tag by name, validates the existance of the tag, if it doesn't exists then is created
     *
     * @param name    name of the tag to get
     * @param hostId  host identifier
     * @param persona True if is a persona key tag
     * @return Tag
     * @throws Exception
     */
    public Tag getTagAndCreate ( String name, String hostId, boolean persona ) throws DotDataException, DotSecurityException {
        return getTagAndCreate(name, "", hostId, persona);
    }

    /**
     * Gets a Tag by name, validates the existance of the tag, if it doesn't exists then is created
	 * @param name name of the tag to get
	 * @param userId owner of the tag
	 * @param hostId host identifier
     * @param persona True if is a persona key tag
     * @return Tag
     * @throws Exception
     */
    public Tag getTagAndCreate ( String name, String userId, String hostId, boolean persona ) throws DotDataException, DotSecurityException {

        boolean localTransaction = false;

        try {

            //Check for a transaction and start one if required
            localTransaction = HibernateUtil.startLocalTransactionIfNeeded();

            Tag newTag = new Tag();

            //Search for tags with this given name
            Tag tag = tagFactory.getTagByNameAndHost(name, hostId);

            // if doesn't exists then the tag is created
            if ( tag == null || !UtilMethods.isSet(tag.getTagId()) ) {
                // creating tag
                return saveTag(name, userId, hostId, persona);
            } else {

                String existHostId;

                //check if global tag already exists
                boolean globalTagExists = false;

                //check if tag exists with same tag name but for a different host
                boolean tagExists = false;

                Host host = APILocator.getHostAPI().find(hostId, APILocator.getUserAPI().getSystemUser(), true);
                if ( host.getMap().get("tagStorage") == null ) {
                    existHostId = host.getMap().get("identifier").toString();
                } else {
                    existHostId = host.getMap().get("tagStorage").toString();
                }

                if ( isGlobalTag(tag) ) {
                    newTag = tag;
                    globalTagExists = true;
                }
                if ( tag.getHostId().equals(existHostId) ) {
                    newTag = tag;
                    tagExists = true;
                }

                if ( !globalTagExists ) {
                    //if global doesn't exist, then save the tag and after it checks if it was stored as a global tag
                    if ( !tagExists ) {
                        newTag = saveTag(name, userId, hostId, persona);
                    }

                    if ( newTag.getHostId().equals(Host.SYSTEM_HOST) ) {
                        //move references of non-global tags to new global tag and delete duplicate non global tags
                        List<TagInode> tagInodes = getTagInodesByTagId(tag.getTagId());
                        for ( TagInode tagInode : tagInodes ) {
                            tagFactory.updateTagInode(tagInode, newTag.getTagId());
                        }
                        deleteTag(tag);
                    }
                }
            }

            //Everything ok..., committing the transaction
            if ( localTransaction ) {
                HibernateUtil.commitTransaction();
            }

            return newTag;

        } catch ( Exception e ) {
            if ( localTransaction ) {
                HibernateUtil.rollbackTransaction();
            }
            throw e;
        }

    }

    /**
	 * Gets a Tag by a tagId retrieved from a TagInode.
	 *
	 * @param tagId the tag id to get
	 * @return tag
	 */
    public Tag getTagByTagId ( String tagId ) throws DotDataException {
        return tagFactory.getTagByTagId(tagId);
    }

    /**
	 * Get the tags seaching by Tag Name and Host identifier 
	 * @param name Tag name
	 * @param hostId Host identifier
	 * @return Tag
     * @throws DotDataException
     */
    public Tag getTagByNameAndHost ( String name, String hostId ) throws DotDataException {
        return tagFactory.getTagByNameAndHost(name, hostId);
    }

    /**
	 * Creates a new tag
	 * @param tagName name of the new tag
	 * @param userId owner of the new tag
	 * @param hostId host identifier
	 * @return Tag
	 * @throws Exception
	 */
    public Tag saveTag ( String tagName, String userId, String hostId ) throws DotDataException {
        return saveTag(tagName, userId, hostId, false);
    }

    /**
	 * Creates a new tag
	 * @param tagName name of the new tag
	 * @param userId owner of the new tag
	 * @param hostId host identifier
	 * @param persona indicate if a persona tag
	 * @return Tag
	 * @throws Exception
	 */
    public Tag saveTag ( String tagName, String userId, String hostId, boolean persona ) throws DotDataException {

        boolean localTransaction = false;

        try {

            //Check for a transaction and start one if required
            localTransaction = HibernateUtil.startLocalTransactionIfNeeded();

            Tag tag = new Tag();
            //creates new Tag
            tag.setTagName(tagName.toLowerCase());
            tag.setUserId(userId);
            tag.setPersona(persona);
            tag.setModDate(new Date());

            Host host = null;

            if ( UtilMethods.isSet(hostId) && !hostId.equals(Host.SYSTEM_HOST) ) {
                try {
                    if ( !UtilMethods.isSet(hostId) ) {
                        host = APILocator.getHostAPI().findDefaultHost(APILocator.getUserAPI().getSystemUser(), true);
                    } else {
                        host = APILocator.getHostAPI().find(hostId, APILocator.getUserAPI().getSystemUser(), true);
                    }
                } catch ( Exception e ) {
                    Logger.error(this, "Unable to load host.");
                }

                if ( host.getMap().get("tagStorage") == null ) {
                    hostId = host.getMap().get("identifier").toString();
                } else {
                    hostId = host.getMap().get("tagStorage").toString();
                }

            } else {
                hostId = Host.SYSTEM_HOST;
            }
            tag.setHostId(hostId);

            Tag createdTag = tagFactory.createTag(tag);

            //Everything ok..., committing the transaction
            if ( localTransaction ) {
                HibernateUtil.commitTransaction();
            }

            return createdTag;
        } catch ( Exception e ) {
            if ( localTransaction ) {
                HibernateUtil.rollbackTransaction();
            }
            throw e;
        }
    }

    /**
	 * Tags an object, validates the existence of a tag(s), creates it if it doesn't exists
	 * and then tags the object
	 * @param tagName tag(s) to create
	 * @param userId owner of the tag
	 * @param inode object to tag
     * @param fieldVarName var name of the tag field related to the inode if the inode belongs to a Contentlet otherwise
     *                     send null
     * @return a list of all tags assigned to an object
     * @deprecated it doesn't handle host id. Call getTagsInText then addTagInode on each
     * @throws Exception
     */
    public List addTag ( String tagName, String userId, String inode, String fieldVarName ) throws DotDataException, DotSecurityException {

        boolean localTransaction = false;

        try {

            //Check for a transaction and start one if required
            localTransaction = HibernateUtil.startLocalTransactionIfNeeded();

            StringTokenizer tagNameToken = new StringTokenizer(tagName, ",");
            if ( tagNameToken.hasMoreTokens() ) {
                for (; tagNameToken.hasMoreTokens(); ) {
                    String tagTokenized = tagNameToken.nextToken().trim();
                    Tag createdTag = getTagAndCreate(tagTokenized, userId, "");
                    addTagInode(createdTag, inode, fieldVarName);
                }
            }

            List<TagInode> tagInodes = getTagInodesByInode(inode);

            //Everything ok..., committing the transaction
            if ( localTransaction ) {
                HibernateUtil.commitTransaction();
            }

            return tagInodes;
        } catch ( Exception e ) {
            if ( localTransaction ) {
                HibernateUtil.rollbackTransaction();
            }
            throw e;
        }
    }

    /**
	 * Updates an existing tag.
	 * @param tagId tag to update
	 * @param tagName owner of the tag
     * @throws DotDataException
     */
    public void updateTag ( String tagId, String tagName ) throws DotDataException {
        updateTag(tagId, tagName, false, Host.SYSTEM_HOST);
    }

    /**
	 * Updates an existing tag.
	 * @param tagId tag to update
	 * @param tagName owner of the tag
	 * @param updateTagReference
	 * @param hostId the storage host id
	 * @throws Exception
	 */
    public void updateTag ( String tagId, String tagName, boolean updateTagReference, String hostId ) throws DotDataException {

        Tag tag = getTagByTagId(tagId);
        boolean tagAlreadyExistsForNewTagStorage = false;

        //This block of code prevent saving duplicated tags when editing tag storage from host
        List<Tag> tags = getTagsByName(tagName);

        for ( Tag t : tags ) {
            if ( t.getHostId().equals(hostId) ) {
                //The tag with new tag storage already exists
                tagAlreadyExistsForNewTagStorage = true;
            }
            if ( t.getTagId().equals(tagId) ) {
                //select tag to be updated
                tag = t;
            }
        }

        //update selected tag if it's set and if previous tag storage is different.
        if ( UtilMethods.isSet(tag.getTagId()) && !tagAlreadyExistsForNewTagStorage ) {
            tag.setTagName(tagName.toLowerCase());
            tag.setUserId("");
            if ( updateTagReference ) {
                if ( UtilMethods.isSet(hostId) )
                    tag.setHostId(hostId);
            }

            tag.setModDate(new Date());
            tagFactory.updateTag(tag);
        }

    }

    /**
     * Updates the persona attribute of a given tag
     *
     * @param tagId
     * @param enableAsPersona
     * @throws DotDataException
     */
    public void enableDisablePersonaTag ( String tagId, boolean enableAsPersona ) throws DotDataException {

        //First check if the requested tag exist
        Tag foundTag = getTagByTagId(tagId);
        if ( foundTag != null && UtilMethods.isSet(foundTag.getTagId()) ) {

            if ( foundTag.isPersona() == enableAsPersona ) {
                return;//Nothing to update
            }

            foundTag.setPersona(enableAsPersona);
            //Update the tag
            tagFactory.updateTag(foundTag);
        }
    }

    /**
	 * Deletes a tag
	 * @param tag tag to be deleted
     * @throws DotDataException
     */
    public void deleteTag ( Tag tag ) throws DotDataException {
        List<TagInode> tagInodes = getTagInodesByTagId(tag.getTagId());
        for ( TagInode t : tagInodes ) {
            deleteTagInode(t);
        }

        tagFactory.deleteTag(tag);
    }

    /**
     * Deletes a tag
     * @param tagId tagId of the tag to be deleted
     * @throws DotDataException
     */
    public void deleteTag ( String tagId ) throws DotDataException {
        Tag tag = getTagByTagId(tagId);
        deleteTag(tag);
    }

    /**
	 * Renames a tag
	 * @param tagName new tag name
	 * @param oldTagName current tag name
	 * @param userId owner of the tag
	 */
    public void editTag ( String tagName, String oldTagName, String userId ) throws DotDataException {

        tagName = escapeSingleQuote(tagName);
        oldTagName = escapeSingleQuote(oldTagName);

        List<Tag> tagToEdit = getTagsByName(oldTagName);
        Iterator it = tagToEdit.iterator();
        while ( it.hasNext() ) {
            Tag tag = (Tag) it.next();

            tag.setTagName(tagName.toLowerCase());
            tag.setModDate(new Date());

            tagFactory.updateTag(tag);
        }
    }

    /**
	 * Gets a tagInode and a host identifier, if doesn't exists then the tagInode it's created
	 *
	 * @param tagName name of the tag
	 * @param inode   inode of the object tagged
	 * @param hostId  the identifier of host that storage the tag
     * @param fieldVarName var name of the tag field related to the inode if the inode belongs to a Contentlet otherwise
     *                     send null
     * @return TagInode
     * @throws DotDataException
     * @throws DotSecurityException
     */
    public TagInode addTagInode ( String tagName, String inode, String hostId, String fieldVarName ) throws DotDataException, DotSecurityException {

        //Ensure the tag exists in the tag table
        Tag existingTag = getTagAndCreate(tagName, "", hostId);

        //Create the the tag inode
        return addTagInode(existingTag, inode, fieldVarName);
    }

    /**
	 * Gets a tagInode and a host identifier, if doesn't exists then the tagInode it's created
	 * @param tag
	 * @param inode inode of the object tagged
     * @param fieldVarName var name of the tag field related to the inode if the inode belongs to a Contentlet otherwise
     *                     send null
     * @return TagInode
     * @throws DotDataException
     */
    public TagInode addTagInode ( Tag tag, String inode, String fieldVarName ) throws DotDataException {

        //validates the tagInode already exists
        TagInode existingTagInode = getTagInode(tag.getTagId(), inode, fieldVarName);

        if ( existingTagInode == null || existingTagInode.getTagId() == null ) {

            //the tagInode does not exists, so creates a new TagInode
            TagInode tagInode = new TagInode();
            tagInode.setTagId(tag.getTagId());
            tagInode.setInode(inode);
            tagInode.setFieldVarName(fieldVarName);
            tagInode.setModDate(new Date());

            return tagFactory.createTagInode(tagInode);
        } else {
            // returning the existing tagInode
            return existingTagInode;
        }
    }

    /**
	 * Gets all tagInode associated to an object
     * @param inode inode of the object tagged
	 * @return list of all the TagInode where the tags are associated to the object
     * @throws DotDataException
     */
    public List<TagInode> getTagInodesByInode ( String inode ) throws DotDataException {
        return tagFactory.getTagInodesByInode(inode);
    }

    /**
	 * Gets all tags associated to an object
	 * @param tagId tagId of the object tagged
	 * @return list of all the TagInode where the tags are associated to the object
     * @throws DotDataException
     */
    public List<TagInode> getTagInodesByTagId ( String tagId ) throws DotDataException {
        return tagFactory.getTagInodesByTagId(tagId);
    }

    /**
	 * Gets a tagInode by name and inode
	 * @param tagId id of the tag
	 * @param inode inode of the object tagged
     * @param fieldVarName var name of the tag field related to the inode if the inode belongs to a Contentlet otherwise
     *                     send null
     * @return the tagInode
     * @throws DotDataException
     */
    public TagInode getTagInode ( String tagId, String inode, String fieldVarName ) throws DotDataException {
        return tagFactory.getTagInode(tagId, inode, fieldVarName);
    }

    /**
	 * Deletes a TagInode
	 * @param tagInode TagInode to delete
     * @throws DotDataException
     */
    public void deleteTagInode ( TagInode tagInode ) throws DotDataException {
        tagFactory.deleteTagInode(tagInode);
    }

    /**
	 * Removes the relationship between a tag and an inode, ALSO <strong>if the tag does not have more relationships the Tag itself will be remove it.</strong>
	 * @param tagId TagId
	 * @param inode inode of the object tagged
     * @param fieldVarName var name of the tag field related to the inode if the inode belongs to a Contentlet otherwise
     *                     send null
     * @throws DotDataException
     */
    public void removeTagRelationAndTagWhenPossible ( String tagId, String inode, String fieldVarName ) throws DotDataException {

        boolean localTransaction = false;

        try {

            //Check for a transaction and start one if required
            localTransaction = HibernateUtil.startLocalTransactionIfNeeded();

            Boolean existRelationship = false;
            //Get the tag inode we want to remove
            TagInode tagInodeToRemove = tagFactory.getTagInode(tagId, inode, fieldVarName);
            if ( UtilMethods.isSet(tagInodeToRemove) && UtilMethods.isSet(tagInodeToRemove.getTagId()) ) {
                existRelationship = true;
            }

            //Get the tag we want to remove
            Tag tagToRemove = tagFactory.getTagByTagId(tagId);

            //First lets search for the relationships of this tag
            List<TagInode> tagInodes = APILocator.getTagAPI().getTagInodesByTagId(tagId);

            if ( tagInodes != null && !tagInodes.isEmpty() ) {

                if ( !existRelationship && tagInodes.size() > 0 ) {
                    //This mean we can NOT remove the tag as it is still related with other inodes
                    return;
                } else if ( existRelationship && tagInodes.size() > 1 ) {//Current relation and more??

                    //Delete the tag inode relationship
                    tagFactory.deleteTagInode(tagInodeToRemove);

                    //And this mean we can NOT remove the tag as it is still related with other inodes
                    return;
                }
            }

            if ( existRelationship ) {
                //Delete the tag inode relationship
                tagFactory.deleteTagInode(tagInodeToRemove);
            }

            //If this tag has not relationships remove it
            if ( UtilMethods.isSet(tagToRemove) && UtilMethods.isSet(tagToRemove.getTagId()) ) {
                tagFactory.deleteTag(tagToRemove);
            }

            //Everything ok..., committing the transaction
            if ( localTransaction ) {
                HibernateUtil.commitTransaction();
            }

        } catch ( Exception e ) {
            if ( localTransaction ) {
                HibernateUtil.rollbackTransaction();
            }
            throw e;
        }

    }

    /**
     * Deletes TagInodes references by inode
     *
     * @param inode inode reference to delete
     * @throws DotDataException
     */
    public void deleteTagInodesByInode(String inode) throws DotDataException {
        tagFactory.deleteTagInodesByInode(inode);
    }

    /**
	 * Deletes a TagInode
	 * @param tag Tag related to the object
	 * @param inode Inode of the object tagged
     * @param fieldVarName var name of the tag field related to the inode if the inode belongs to a Contentlet otherwise
     *                     send null
     * @throws DotDataException
     */
    public void deleteTagInode ( Tag tag, String inode, String fieldVarName ) throws DotDataException {

        TagInode tagInode = getTagInode(tag.getTagId(), inode, fieldVarName);
        if ( tagInode != null && UtilMethods.isSet(tagInode.getTagId()) ) {
            deleteTagInode(tagInode);
        }
    }

    /**
     * Deletes an object tag assignment
     *
     * @param tagName name of the tag
     * @param inode   inode of the object tagged
     * @param fieldVarName var name of the tag field related to the inode if the inode belongs to a Contentlet otherwise
     *                     send null
     * @throws DotSecurityException
     * @throws DotDataException
     */
    public void deleteTagInode ( String tagName, String inode, String fieldVarName ) throws DotSecurityException, DotDataException {

        StringTokenizer tagNameToken = new StringTokenizer(tagName, ",");
        if ( tagNameToken.hasMoreTokens() ) {

            for (; tagNameToken.hasMoreTokens(); ) {

                String tagTokenized = tagNameToken.nextToken().trim();

                //Search for tags with the given name
                List<Tag> foundTags = getTagsByName(tagTokenized);
                if ( foundTags != null && !foundTags.isEmpty() ) {

                    for ( Tag foundTag : foundTags ) {
                        //Delete the related tag inode
                        deleteTagInode(foundTag, inode, fieldVarName);
                    }
                }
            }
        }
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
	 * Gets a suggested tag(s), by name
	 * @param name name of the tag searched
	 * @param selectedHostId Host identifier
	 * @return list of suggested tags
	 */
    @SuppressWarnings ( "unchecked" )
    public List<Tag> getSuggestedTag ( String name, String selectedHostId ) {
        try {
            name = escapeSingleQuote(name);

            //if there's a host field on form, retrieve it
            Host hostOnForm;
            if ( UtilMethods.isSet(selectedHostId) ) {
                try {
                    hostOnForm = APILocator.getHostAPI().find(selectedHostId, APILocator.getUserAPI().getSystemUser(), true);
                    selectedHostId = hostOnForm.getMap().get("tagStorage").toString();
                } catch ( Exception e ) {
                    Logger.error(this, "Unable to load current host.");
                }
            }

            return tagFactory.getTagsLikeNameAndHostIncludingSystemHost(name, selectedHostId);
        } catch ( Exception e ) {
            Logger.error(e, "Error retrieving suggested tags");
        }
        return new ArrayList<>();
    }

    /**
	 * Check if tag is global
	 * @param tag
	 * @return boolean
	 */
    private boolean isGlobalTag ( Tag tag ) {
        if ( tag.getHostId().equals(Host.SYSTEM_HOST) )
            return true;
        else
            return false;
    }

    /**
	 * Update, copy or move tags if the hosst changes its tag storage
	 * @param oldTagStorageId
	 * @param newTagStorageId
	 */
    public void updateTagReferences ( String hostIdentifier, String oldTagStorageId, String newTagStorageId ) throws DotDataException {

        boolean localTransaction = false;

        try {

            if ( !oldTagStorageId.equals(Host.SYSTEM_HOST) && !oldTagStorageId.equals(newTagStorageId) ) {

                //Check for a transaction and start one if required
                localTransaction = HibernateUtil.startLocalTransactionIfNeeded();

                //copy or update tags if the tag storage id has changed when editing the host
                //or if the previous tag storage was global
                List<Tag> list = tagFactory.getTagsByHost(oldTagStorageId);

                List<Tag> hostTagList = tagFactory.getTagsByHost(hostIdentifier);

                for ( Tag tag : list ) {
                    try {
                        if ( (hostIdentifier.equals(newTagStorageId) && hostTagList.size() == 0) && !newTagStorageId.equals(Host.SYSTEM_HOST) ) {
                            //copy old tag to host with new tag storage
                            saveTag(tag.getTagName(), "", hostIdentifier);
                        } else if ( newTagStorageId.equals(Host.SYSTEM_HOST) ) {
                            //update old tag to global tags
                            getTagAndCreate(tag.getTagName(), Host.SYSTEM_HOST);
                        } else if ( hostIdentifier.equals(newTagStorageId) && hostTagList.size() > 0 || hostIdentifier.equals(oldTagStorageId) ) {
                            // update old tag with new tag storage
                            updateTag(tag.getTagId(), tag.getTagName(), true, newTagStorageId);
                        }

                    } catch ( Exception e ) {
                        Logger.error(e, "Error updating Tag references");
                    }
                }

                //Everything ok..., committing the transaction
                if ( localTransaction ) {
                    HibernateUtil.commitTransaction();
                }
            }
        } catch ( Exception e ) {
            if ( localTransaction ) {
                HibernateUtil.rollbackTransaction();
            }
            throw e;
        }

    }

    /**
	 * Gets all tags associated to an object
	 * @param inode object inode
	 * @return List<Tag>
     * @throws DotDataException
     */
    @Override
    public List<Tag> getTagsByInode ( String inode ) throws DotDataException {
        return tagFactory.getTagsByInode(inode);
    }

    /**
     * Extract tag names in the specified text and return the list
     * of Tag Object found
     *
     * @param text   tag name to search
     * @param hostId Host identifier
     * @return list of tag found
     * @throws Exception
     */
    @Override
    public List<Tag> getTagsInText ( String text, String hostId ) throws DotSecurityException, DotDataException {
        return getTagsInText(text, "", hostId);
    }

    /**
     * Extract tag names in the specified text and return the list
	 * of Tag Object found
	 * 
	 * @param text tag name to search
	 * @param userId User id
	 * @param hostId Host identifier
	 * @return list of tag found
	 * @throws Exception 
	 */
    @Override
    public List<Tag> getTagsInText ( String text, String userId, String hostId ) throws DotSecurityException, DotDataException {

        List<Tag> tags = new ArrayList<>();

        //Split the given list of tasks
        String[] tagNames = text.split("[,\\n\\t\\r]");
        for ( String tagname : tagNames ) {
            tagname = tagname.trim();
            if ( tagname.length() > 0 ) {
                //Search for this given tag and create it if does not exist
                tags.add(getTagAndCreate(tagname, userId, hostId));
            }
        }

        return tags;
    }

}
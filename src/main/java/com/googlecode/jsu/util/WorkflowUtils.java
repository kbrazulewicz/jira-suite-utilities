package com.googlecode.jsu.util;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.util.IssueChangeHolder;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.ActionDescriptor;

import java.util.Collection;
import java.util.List;

/**
 * @author Krystian Brazulewicz
 */
public interface WorkflowUtils
{
    static final String SPLITTER = "@@";

    /**
     * @param key
     * @return a String with the field name from given key.
     */
    String getFieldNameFromKey(String key);

    /**
     * @param key
     * @return a Field object from given key. (Field or Custom Field).
     */
    Field getFieldFromKey(String key);

    Field getFieldFromDescriptor(AbstractDescriptor descriptor, String name);

    /**
     * @param issue
     *            an issue object.
     * @param field
     *            a field object. (May be a Custom Field)
     * @return an Object
     *
     * It returns the value of a field within issue object. May be a Collection,
     * a List, a Strong, or any FildType within JIRA.
     *
     */
    Object getFieldValueFromIssue(Issue issue, Field field);

    /**
     * Sets specified value to the field for the issue.
     *
     * @param issue
     * @param field
     * @param value
     */
    void setFieldValue(MutableIssue issue, Field field, Object value, IssueChangeHolder changeHolder);


    /**
     * Method sets value for issue field. Field was defined as string
     *
     * @param issue
     *            Muttable issue for changing
     * @param fieldKey
     *            Field name
     * @param value
     *            Value for setting
     */
    void setFieldValue(
            MutableIssue issue, String fieldKey, Object value,
            IssueChangeHolder changeHolder
    );

    /**
     * @param strGroups
     * @param splitter
     * @return a List of Group
     *
     * Get Groups from a string.
     *
     */
    List<com.atlassian.crowd.embedded.api.Group> getGroups(String strGroups, String splitter);

    /**
     * @param groups
     * @param splitter
     * @return a String with the groups selected.
     *
     * Get Groups as String.
     *
     */
    String getStringGroup(Collection<com.atlassian.crowd.embedded.api.Group> groups, String splitter);

    /**
     * @param strFields
     * @param splitter
     * @return a List of Field
     *
     * Get Fields from a string.
     *
     */
    List<Field> getFields(String strFields, String splitter);

    /**
     * @param fields
     * @param splitter
     * @return a String with the fields selected.
     *
     * Get Fields as String.
     *
     */
    String getStringField(Collection<Field> fields, String splitter);

    /**
     * @param actionDescriptor
     * @return the FieldScreen of the transition. Or null, if the transition
     *         hasn't a screen asociated.
     *
     * It obtains the fieldscreen for a transition, if it have one.
     *
     */
    FieldScreen getFieldScreen(ActionDescriptor actionDescriptor);
}

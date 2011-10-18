package com.googlecode.jsu.util;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutStorageException;
import com.atlassian.jira.issue.fields.screen.FieldScreen;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

/**
 * @author Krystian Brazulewicz
 */
public interface FieldCollectionsUtils
{
    /**
     * @return a complete list of fields, including custom fields.
     */
    List<Field> getAllFields();

    /**
     * @return a list of fields, including custom fields, which could be modified.
     */
    List<Field> getAllEditableFields();

    /**
     * @param allFields list of fields to be sorted.
     * @return a list with fields sorted by name.
     */
    List<Field> sortFields(List<Field> allFields);

    /**
     * @return a list of all fields of type date and datetime.
     */
    List<Field> getAllDateFields();

    /**
     * @param issue: issue to which the field belongs
     * @param field wished field
     * @param fieldScreen wished screen
     * @return if a field is displayed in a screen.
     */
    boolean isFieldOnScreen(Issue issue, Field field, FieldScreen fieldScreen);

    /*
   It's not possible to put a validation message on a timetracking field.
    */
    boolean cannotSetValidationMessageToField(Field field);

    /**
     * Check is the issue has the field.
     *
     * @param issue: issue to which the field belongs
     * @param field: wished field
     * @return if a field is available.
     */
    boolean isIssueHasField(Issue issue, Field field);

    FieldLayoutItem getFieldLayoutItem(Issue issue, Field field) throws FieldLayoutStorageException;

    /**
     * @param issue: issue to which the field belongs
     * @param field: wished field
     * @return if a field is required.
     */
    boolean isFieldRequired(Issue issue, Field field);

    /**
     * @return a list of fields that could be chosen to copy their value.
     */
    List<Field> getCopyFromFields();

    /**
     * @return a list of fields that could be chosen to copy their value.
     */
    List<Field> getCopyToFields();

    /**
     * @return a list of fields that could be chosen like required.
     */
    List<Field> getRequirableFields();

    /**
     * @return a list of fields that could be chosen in Value-Field Condition.
     */
    List<Field> getValueFieldConditionFields();

    /**
     * @param tsDate
     * @return a String.
     *
     * It formats to a date nice.
     */
    String getNiceDate(Timestamp tsDate);

    /**
     * @param cal
     *
     * Clear the time part from a given Calendar.
     *
     */
    void clearCalendarTimePart(Calendar cal);
}

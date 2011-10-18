package com.googlecode.jsu.util;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.atlassian.crowd.embedded.api.CrowdService;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.user.util.UserManager;
import org.apache.commons.lang.StringUtils;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.bc.project.component.ProjectComponentManager;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueConstant;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.IssueRelationConstants;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.MultipleCustomFieldType;
import com.atlassian.jira.issue.customfields.impl.AbstractMultiCFType;
import com.atlassian.jira.issue.customfields.impl.CascadingSelectCFType;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.view.CustomFieldParams;
import com.atlassian.jira.issue.customfields.view.CustomFieldParamsImpl;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutStorageException;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.security.IssueSecurityLevelManager;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.util.IssueChangeHolder;
import com.atlassian.jira.issue.worklog.WorkRatio;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.util.ObjectUtils;
import com.atlassian.jira.workflow.WorkflowActionsBean;
import com.opensymphony.user.Entity;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.crowd.embedded.api.User;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;

/**
 * @author Gustavo Martin.
 *
 * This utils class exposes common methods to custom workflow objects.
 *
 */
public class WorkflowUtilsImpl implements WorkflowUtils {
    private static final String CASCADING_SELECT_TYPE = "com.atlassian.jira.plugin.system.customfieldtypes:cascadingselect";

    private final WorkflowActionsBean workflowActionsBean = new WorkflowActionsBean();
    private final Logger log = LoggerFactory.getLogger(WorkflowUtils.class);

    private final FieldManager fieldManager;
    private final IssueManager issueManager;
    private final ProjectComponentManager projectComponentManager;
    private final VersionManager versionManager;
    private final IssueSecurityLevelManager issueSecurityLevelManager;
    private final ApplicationProperties applicationProperties;
    private final FieldCollectionsUtils fieldCollectionsUtils;
    private final IssueLinkManager issueLinkManager;
    private final UserManager userManager;
    private final CrowdService crowdService;

    /**
     * @param fieldManager
     * @param issueManager
     * @param projectComponentManager
     * @param versionManager
     * @param issueSecurityLevelManager
     * @param applicationProperties
     * @param fieldCollectionsUtils
     * @param issueLinkManager
     */
    public WorkflowUtilsImpl(
            FieldManager fieldManager, IssueManager issueManager,
            ProjectComponentManager projectComponentManager, VersionManager versionManager,
            IssueSecurityLevelManager issueSecurityLevelManager, ApplicationProperties applicationProperties,
            FieldCollectionsUtils fieldCollectionsUtils, IssueLinkManager issueLinkManager,
            UserManager userManager, CrowdService crowdService
    ) {
        this.fieldManager = fieldManager;
        this.issueManager = issueManager;
        this.projectComponentManager = projectComponentManager;
        this.versionManager = versionManager;
        this.issueSecurityLevelManager = issueSecurityLevelManager;
        this.applicationProperties = applicationProperties;
        this.fieldCollectionsUtils = fieldCollectionsUtils;
        this.issueLinkManager = issueLinkManager;
        this.userManager = userManager;
        this.crowdService = crowdService;
    }

    @Override
    public String getFieldNameFromKey(String key) {
        return getFieldFromKey(key).getName();
    }

    @Override
    public Field getFieldFromKey(String key) {
        Field field;

        if (fieldManager.isCustomField(key)) {
            field = fieldManager.getCustomField(key);
        } else {
            field = fieldManager.getField(key);
        }

        if (field == null) {
            throw new IllegalArgumentException("Unable to find field '" + key + "'");
        }

        return field;
    }

    @Override
    public Field getFieldFromDescriptor(AbstractDescriptor descriptor, String name) {
        FunctionDescriptor functionDescriptor = (FunctionDescriptor) descriptor;
        Map args = functionDescriptor.getArgs();
        String fieldKey = (String) args.get(name);

        return getFieldFromKey(fieldKey);
    }

    @Override
    public Object getFieldValueFromIssue(Issue issue, Field field) {
        Object retVal = null;

        try {
            if (fieldManager.isCustomField(field)) {
                // Return the CustomField value. It could be any object.
                CustomField customField = (CustomField) field;
                Object value = issue.getCustomFieldValue(customField);

                // TODO Maybe for cascade we have to create separate manager
                if (CASCADING_SELECT_TYPE.equals(customField.getCustomFieldType().getKey())) {
                    CustomFieldParams params = (CustomFieldParams) value;

                    if (params != null) {
                        Option parent = (Option) params.getFirstValueForNullKey();
                        Option child = (Option) params.getFirstValueForKey(CascadingSelectCFType.CHILD_KEY);

                        if (parent != null) {
                            if (ObjectUtils.isValueSelected(child)) {
                                retVal = child.toString();
                            } else {
                                final List<Option> childOptions = parent.getChildOptions();

                                if ((childOptions == null) || (childOptions.isEmpty())) {
                                    retVal = parent.toString();
                                }
                            }
                        }
                    }
                } else {
                    retVal = value;
                }

                if (log.isDebugEnabled()) {
                    log.debug(
                            String.format(
                                    "Got field value [object=%s;class=%s]",
                                    retVal, ((retVal != null) ? retVal.getClass() : "")
                            )
                    );
                }
            } else {
                String fieldId = field.getId();
                Collection<?> retCollection = null;

                // Special treatment of fields.
                if (fieldId.equals(IssueFieldConstants.ATTACHMENT)) {
                    // return a collection with the attachments associated to given issue.
                    retCollection = issue.getAttachments();

                    if (retCollection != null && !retCollection.isEmpty()) {
                        retVal = retCollection;
                    }
                } else if (fieldId.equals(IssueFieldConstants.AFFECTED_VERSIONS)) {
                    retCollection = issue.getAffectedVersions();

                    if (retCollection != null && !retCollection.isEmpty()) {
                        retVal = retCollection;
                    }
                } else if (fieldId.equals(IssueFieldConstants.COMMENT)) {
                    // return a list with the comments of a given issue.
                    try {
                        retCollection = issueManager.getEntitiesByIssueObject(
                                IssueRelationConstants.COMMENTS, issue
                        );

                        if (retCollection != null && !retCollection.isEmpty()) {
                            retVal = retCollection;
                        }
                    } catch (GenericEntityException e) {
                        retVal = null;
                    }
                } else if (fieldId.equals(IssueFieldConstants.COMPONENTS)) {
                    retCollection = issue.getComponents();

                    if (retCollection != null && !retCollection.isEmpty()) {
                        retVal = retCollection;
                    }
                } else if (fieldId.equals(IssueFieldConstants.FIX_FOR_VERSIONS)) {
                    retCollection = issue.getFixVersions();

                    if (retCollection != null && !retCollection.isEmpty()) {
                        retVal = retCollection;
                    }
                } else if (fieldId.equals(IssueFieldConstants.THUMBNAIL)) {
                    // Not implemented, yet.
                } else if (fieldId.equals(IssueFieldConstants.ISSUE_TYPE)) {
                    retVal = issue.getIssueTypeObject();
                } else if (fieldId.equals(IssueFieldConstants.TIMETRACKING)) {
                    // Not implemented, yet.
                } else if (fieldId.equals(IssueFieldConstants.ISSUE_LINKS)) {
                    retVal = issueLinkManager.getIssueLinks(issue.getId());
                } else if (fieldId.equals(IssueFieldConstants.WORKRATIO)) {
                    retVal = String.valueOf(WorkRatio.getWorkRatio(issue));
                } else if (fieldId.equals(IssueFieldConstants.ISSUE_KEY)) {
                    retVal = issue.getKey();
                } else if (fieldId.equals(IssueFieldConstants.SUBTASKS)) {
                    retCollection = issue.getSubTaskObjects();

                    if (retCollection != null && !retCollection.isEmpty()) {
                        retVal = retCollection;
                    }
                } else if (fieldId.equals(IssueFieldConstants.PRIORITY)) {
                    retVal = issue.getPriorityObject();
                } else if (fieldId.equals(IssueFieldConstants.RESOLUTION)) {
                    retVal = issue.getResolutionObject();
                } else if (fieldId.equals(IssueFieldConstants.STATUS)) {
                    retVal = issue.getStatusObject();
                } else if (fieldId.equals(IssueFieldConstants.PROJECT)) {
                    retVal = issue.getProjectObject();
                } else if (fieldId.equals(IssueFieldConstants.SECURITY)) {
                    retVal = issue.getSecurityLevel();
                } else if (fieldId.equals(IssueFieldConstants.TIME_ESTIMATE)) {
                    retVal = issue.getEstimate();
                } else if (fieldId.equals(IssueFieldConstants.TIME_SPENT)) {
                    retVal = issue.getTimeSpent();
                } else if (fieldId.equals(IssueFieldConstants.ASSIGNEE)) {
                    retVal = issue.getAssigneeUser();
                } else if (fieldId.equals(IssueFieldConstants.REPORTER)) {
                    retVal = issue.getReporterUser();
                } else if (fieldId.equals(IssueFieldConstants.DESCRIPTION)) {
                    retVal = issue.getDescription();
                } else if (fieldId.equals(IssueFieldConstants.ENVIRONMENT)) {
                    retVal = issue.getEnvironment();
                } else if (fieldId.equals(IssueFieldConstants.SUMMARY)) {
                    retVal = issue.getSummary();
                } else if (fieldId.equals(IssueFieldConstants.DUE_DATE)) {
                    retVal = issue.getDueDate();
                } else if (fieldId.equals(IssueFieldConstants.UPDATED)) {
                    retVal = issue.getUpdated();
                } else if (fieldId.equals(IssueFieldConstants.CREATED)) {
                    retVal = issue.getCreated();
                } else if (fieldId.equals(IssueFieldConstants.RESOLUTION_DATE)) {
                    retVal = issue.getResolutionDate();
                } else {
                    log.warn("Issue field \"" + fieldId + "\" is not supported.");

                    GenericValue gvIssue = issue.getGenericValue();

                    if (gvIssue != null) {
                        retVal = gvIssue.get(fieldId);
                    }
                }
            }
        } catch (NullPointerException e) {
            retVal = null;

            log.error("Unable to get field \"" + field.getId() + "\" value", e);
        }

        return retVal;
    }

    @Override
    public void setFieldValue(MutableIssue issue, Field field, Object value, IssueChangeHolder changeHolder) {
        if (fieldManager.isCustomField(field)) {
            CustomField customField = (CustomField) field;
            Object oldValue = issue.getCustomFieldValue(customField);
            FieldLayoutItem fieldLayoutItem;
            CustomFieldType cfType = customField.getCustomFieldType();

            if (log.isDebugEnabled()) {
                log.debug(
                        String.format(
                                "Set custom field value " +
                                "[field=%s,type=%s,oldValue=%s,newValueClass=%s,newValue=%s]",
                                customField,
                                cfType,
                                oldValue,
                                (value != null) ? value.getClass().getName() : "null",
                                value
                        )
                );
            }

            try {
                fieldLayoutItem = fieldCollectionsUtils.getFieldLayoutItem(issue, field);
            } catch (FieldLayoutStorageException e) {
                log.error("Unable to get field layout item", e);

                throw new IllegalStateException(e);
            }

            Object newValue = value;

            if (value instanceof IssueConstant) {
                newValue = ((IssueConstant) value).getName();
            } else if (value instanceof Entity) {
                newValue = ((Entity) value).getName();
            } else if (value instanceof GenericValue) {
                final GenericValue gv = (GenericValue) value;

                if ("SchemeIssueSecurityLevels".equals(gv.getEntityName())) { // We got security level
                    newValue = gv.getString("name");
                }
            }

            if (newValue instanceof String) {
                //convert from string to Object
                CustomFieldParams fieldParams = new CustomFieldParamsImpl(customField, newValue);

                newValue = cfType.getValueFromCustomFieldParams(fieldParams);
            } else if (newValue instanceof Collection<?>) {
                if ((customField.getCustomFieldType() instanceof AbstractMultiCFType) ||
                        (customField.getCustomFieldType() instanceof MultipleCustomFieldType)) {
                    // format already correct
                } else {
                    //convert from string to Object
                    CustomFieldParams fieldParams = new CustomFieldParamsImpl(
                            customField,
                            StringUtils.join((Collection<?>) newValue, ",")
                    );

                    newValue = cfType.getValueFromCustomFieldParams(fieldParams);
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Got new value [class=" +
                        ((newValue != null) ? newValue.getClass().getName() : "null") +
                        ",value=" +
                        newValue +
                        "]"
                );
            }

            // Updating internal custom field value
            issue.setCustomFieldValue(customField, newValue);

            customField.updateValue(
                    fieldLayoutItem, issue,
                    new ModifiedValue(oldValue, newValue),	changeHolder
            );

            if (log.isDebugEnabled()) {
                log.debug(
                        "Issue [" +
                        issue +
                        "] got modfied fields - [" +
                        issue.getModifiedFields() +
                        "]"
                );
            }

            // Not new
            if (issue.getKey() != null) {
                // Remove duplicated issue update
                if (issue.getModifiedFields().containsKey(field.getId())) {
                    issue.getModifiedFields().remove(field.getId());
                }
            }
        } else {
            final String fieldId = field.getId();

            // Special treatment of fields.
            if (fieldId.equals(IssueFieldConstants.ATTACHMENT)) {
                throw new UnsupportedOperationException("Not implemented");
                //				// return a collection with the attachments associated to given issue.
                //				retCollection = (Collection)issue.getExternalFieldValue(fieldId);
                //				if(retCollection==null || retCollection.isEmpty()){
                //					isEmpty = true;
                //				}else{
                //					retVal = retCollection;
                //				}
            } else if (fieldId.equals(IssueFieldConstants.AFFECTED_VERSIONS)) {
                if (value == null) {
                    issue.setAffectedVersions(Collections.<Version>emptySet());
                } else if (value instanceof String) {
                    Version v = versionManager.getVersion(issue.getProjectObject().getId(), (String) value);

                    if (v != null) {
                        issue.setAffectedVersions(Arrays.asList(v));
                    } else {
                        throw new IllegalArgumentException("Wrong affected version value");
                    }
                } else if (value instanceof Version) {
                    issue.setAffectedVersions(Arrays.asList((Version) value));
                } else if (value instanceof Collection) {
                    issue.setAffectedVersions((Collection) value);
                } else {
                    throw new IllegalArgumentException("Wrong affected version value");
                }
            } else if (fieldId.equals(IssueFieldConstants.COMMENT)) {
                throw new UnsupportedOperationException("Not implemented");

                //				// return a list with the comments of a given issue.
                //				try {
                //					retCollection = ManagerFactory.getIssueManager().getEntitiesByIssue(IssueRelationConstants.COMMENTS, issue.getGenericValue());
                //					if(retCollection==null || retCollection.isEmpty()){
                //						isEmpty = true;
                //					}else{
                //						retVal = retCollection;
                //					}
                //				} catch (GenericEntityException e) {
                //					retVal = null;
                //				}
            } else if (fieldId.equals(IssueFieldConstants.COMPONENTS)) {
                if (value == null) {
                    issue.setComponents(Collections.<GenericValue>emptySet());
                } else if (value instanceof String) {
                    ProjectComponent v = projectComponentManager.findByComponentName(
                            issue.getProjectObject().getId(), (String) value
                    );

                    if (v != null) {
                        issue.setComponents(Arrays.asList(v.getGenericValue()));
                    }
                } else if (value instanceof GenericValue) {
                    issue.setComponents(Arrays.asList((GenericValue) value));
                } else if (value instanceof Collection) {
                    issue.setComponents((Collection) value);
                } else {
                    throw new IllegalArgumentException("Wrong component value");
                }
            } else if (fieldId.equals(IssueFieldConstants.FIX_FOR_VERSIONS)) {
                if (value == null) {
                    issue.setFixVersions(Collections.<Version>emptySet());
                } else if (value instanceof String) {
                    Version v = versionManager.getVersion(issue.getProjectObject().getId(), (String) value);

                    if (v != null) {
                        issue.setFixVersions(Arrays.asList(v));
                    }
                } else if (value instanceof Version) {
                    issue.setFixVersions(Arrays.asList((Version) value));
                } else if (value instanceof Collection) {
                    issue.setFixVersions((Collection) value);
                } else {
                    throw new IllegalArgumentException("Wrong fix version value");
                }
            } else if (fieldId.equals(IssueFieldConstants.THUMBNAIL)) {
                throw new UnsupportedOperationException("Not implemented");

                //				// Not implemented, yet.
                //				isEmpty = true;
            } else if (fieldId.equals(IssueFieldConstants.ISSUE_TYPE)) {
                throw new UnsupportedOperationException("Not implemented");
                //
                //				retVal = issue.getIssueTypeObject();
            } else if (fieldId.equals(IssueFieldConstants.TIMETRACKING)) {
                throw new UnsupportedOperationException("Not implemented");
                //
                //				// Not implemented, yet.
                //				isEmpty = true;
            } else if (fieldId.equals(IssueFieldConstants.ISSUE_LINKS)) {
                throw new UnsupportedOperationException("Not implemented");
                //
                //				retVal = ComponentManager.getInstance().getIssueLinkManager().getIssueLinks(issue.getId());
            } else if (fieldId.equals(IssueFieldConstants.WORKRATIO)) {
                throw new UnsupportedOperationException("Not implemented");
                //
                //				retVal = String.valueOf(WorkRatio.getWorkRatio(issue));
            } else if (fieldId.equals(IssueFieldConstants.ISSUE_KEY)) {
                throw new UnsupportedOperationException("Not implemented");
                //
                //				retVal = issue.getKey();
            } else if (fieldId.equals(IssueFieldConstants.SUBTASKS)) {
                throw new UnsupportedOperationException("Not implemented");
                //
                //				retCollection = issue.getSubTasks();
                //				if(retCollection==null || retCollection.isEmpty()){
                //					isEmpty = true;
                //				}else{
                //					retVal = retCollection;
                //				}
            } else if (fieldId.equals(IssueFieldConstants.PRIORITY)) {
                if (value == null) {
                    issue.setPriority(null);
                } else {
                    throw new UnsupportedOperationException("Not implemented");
                }
            } else if (fieldId.equals(IssueFieldConstants.RESOLUTION)) {
                if (value == null) {
                    issue.setResolution(null);
                } else if (value instanceof GenericValue) {
                    issue.setResolution((GenericValue) value);
                } else if (value instanceof Resolution) {
                    issue.setResolutionId(((Resolution) value).getId());
                } else if (value instanceof String) {
                    Collection<Resolution> resolutions = ComponentManager.getInstance().getConstantsManager().getResolutionObjects();
                    Resolution resolution = null;
                    String s = ((String) value).trim();

                    for (Resolution r : resolutions) {
                        if (r.getName().equalsIgnoreCase(s)) {
                            resolution = r;

                            break;
                        }
                    }

                    if (resolution != null) {
                        issue.setResolutionId(resolution.getId());
                    } else {
                        throw new IllegalArgumentException("Unable to find resolution with name \"" + value + "\"");
                    }
                } else {
                    throw new UnsupportedOperationException("Not implemented");
                }
            } else if (fieldId.equals(IssueFieldConstants.STATUS)) {
                if (value == null) {
                    issue.setStatus(null);
                } else if (value instanceof GenericValue) {
                    issue.setStatus((GenericValue) value);
                } else if (value instanceof Status) {
                    issue.setStatusId(((Status) value).getId());
                } else if (value instanceof String) {
                    Status status = ComponentManager.getInstance().getConstantsManager().getStatusByName((String) value);

                    if (status != null) {
                        issue.setStatusId(status.getId());
                    } else {
                        throw new IllegalArgumentException("Unable to find status with name \"" + value + "\"");
                    }
                } else {
                    throw new UnsupportedOperationException("Not implemented");
                }
            } else if (fieldId.equals(IssueFieldConstants.PROJECT)) {
                if (value == null) {
                    issue.setProject(null);
                } else {
                    throw new UnsupportedOperationException("Not implemented");
                }
            } else if (fieldId.equals(IssueFieldConstants.SECURITY)) {
                if (value == null) {
                    issue.setSecurityLevel(null);
                } else if (value instanceof GenericValue) {
                    issue.setSecurityLevel((GenericValue) value);
                } else if (value instanceof Long) {
                    issue.setSecurityLevelId((Long) value);
                } else if (value instanceof String) {
                    Collection<GenericValue> levels;

                    try {
                        levels = issueSecurityLevelManager.getSecurityLevelsByName((String) value);
                    } catch (GenericEntityException e) {
                        throw new IllegalArgumentException("Unable to find security level \"" + value + "\"");
                    }

                    if (levels == null) {
                        throw new IllegalArgumentException("Unable to find security level \"" + value + "\"");
                    }

                    if (levels.size() > 1) {
                        throw new IllegalArgumentException("More that one security level with name \"" + value + "\"");
                    }

                    issue.setSecurityLevel(levels.iterator().next());
                } else {
                    throw new UnsupportedOperationException("Not implemented");
                }
            } else if (fieldId.equals(IssueFieldConstants.ASSIGNEE)) {
                if (value == null) {
                    issue.setAssignee(null);
                } else if (value instanceof User) {
                    issue.setAssignee((User) value);
                } else if (value instanceof String) {
                    User user = userManager.getUserObject((String) value);
                    if (null != user) {
                        issue.setAssignee(user);
                    } else {
                        throw new IllegalArgumentException(String.format("User \"%s\" not found", value));
                    }
                }
            } else if (fieldId.equals(IssueFieldConstants.DUE_DATE)) {
                if (value == null) {
                    issue.setDueDate(null);
                }

                if (value instanceof Timestamp) {
                    issue.setDueDate((Timestamp) value);
                } else if (value instanceof String) {
                    SimpleDateFormat formatter = new SimpleDateFormat(
                            applicationProperties.getDefaultString(APKeys.JIRA_DATE_TIME_PICKER_JAVA_FORMAT)
                    );

                    try {
                        Date date = formatter.parse((String) value);

                        if (date != null) {
                            issue.setDueDate(new Timestamp(date.getTime()));
                        } else {
                            issue.setDueDate(null);
                        }
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Wrong date format exception for \"" + value + "\"");
                    }
                }
            } else if (fieldId.equals(IssueFieldConstants.REPORTER)) {
                if (value == null) {
                    issue.setReporter(null);
                } else if (value instanceof User) {
                    issue.setReporter((User) value);
                } else if (value instanceof String) {
                    User user = userManager.getUserObject((String) value);
                    if (user != null) {
                        issue.setReporter(user);
                    } else {
                        throw new IllegalArgumentException(String.format("User \"%s\" not found", value));
                    }
                }
            } else if (fieldId.equals(IssueFieldConstants.SUMMARY)) {
                if ((value == null) || (value instanceof String)) {
                    issue.setSummary((String) value);
                } else {
                    throw new UnsupportedOperationException("Wrong value type for setting 'Summary'");
                }
            } else if (fieldId.equals(IssueFieldConstants.DESCRIPTION)) {
                if ((value == null) || (value instanceof String)) {
                    issue.setDescription((String) value);
                } else {
                    throw new UnsupportedOperationException("Wrong value type for setting 'Description'");
                }
            } else if (fieldId.equals(IssueFieldConstants.ENVIRONMENT)) {
                if ((value == null) || (value instanceof String)) {
                    issue.setEnvironment((String) value);
                } else {
                    throw new UnsupportedOperationException("Wrong value type for setting 'Environment'");
                }
            } else {
                log.error("Issue field \"" + fieldId + "\" is not supported for setting.");
            }
        }
    }

    @Override
    public void setFieldValue(
            MutableIssue issue, String fieldKey, Object value,
            IssueChangeHolder changeHolder
    ) {
        final Field field = getFieldFromKey(fieldKey);

        setFieldValue(issue, field, value, changeHolder);
    }

    @Override
    public List<Group> getGroups(String strGroups, String splitter) {
        String[] groups = strGroups.split("\\Q" + splitter + "\\E");
        List<Group> groupList = new ArrayList<Group>(groups.length);

        for (String s : groups) {
            Group group = crowdService.getGroup(s);
            groupList.add(group);
        }

        return groupList;
    }

    @Override
    public String getStringGroup(Collection<Group> groups, String splitter) {
        StringBuilder sb = new StringBuilder();

        for (Group g : groups) {
            sb.append(g.getName()).append(splitter);
        }

        return sb.toString();
    }

    @Override
    public List<Field> getFields(String strFields, String splitter) {
        String[] fields = strFields.split("\\Q" + splitter + "\\E");
        List<Field> fieldList = new ArrayList<Field>(fields.length);

        for (String s : fields) {
            final Field field = fieldManager.getField(s);

            if (field != null) {
                fieldList.add(field);
            }
        }

        return fieldCollectionsUtils.sortFields(fieldList);
    }

    @Override
    public String getStringField(Collection<Field> fields, String splitter) {
        StringBuilder sb = new StringBuilder();

        for (Field f : fields) {
            sb.append(f.getId()).append(splitter);
        }

        return sb.toString();
    }

    @Override
    public FieldScreen getFieldScreen(ActionDescriptor actionDescriptor) {
        return workflowActionsBean.getFieldScreenForView(actionDescriptor);
    }
}

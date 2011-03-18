package com.googlecode.jsu.customfields;

import com.atlassian.jira.issue.customfields.converters.SelectConverter;
import com.atlassian.jira.issue.customfields.converters.StringConverter;
import com.atlassian.jira.issue.customfields.impl.SelectCFType;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;

/**
 * Wrapper on Jira SelectCFType for using inside plugins v2.
 *
 * @author <A href="mailto:abashev at gmail dot com">Alexey Abashev</A>
 * @version $Id$
 */
public class LocationSelectCFType extends SelectCFType {
    /**
     * @param customFieldValuePersister
     * @param stringConverter
     * @param selectConverter
     * @param optionsManager
     * @param genericConfigManager
     */
    public LocationSelectCFType(
            CustomFieldValuePersister customFieldValuePersister,
            StringConverter stringConverter,
            SelectConverter selectConverter,
            OptionsManager optionsManager,
            GenericConfigManager genericConfigManager
    ) {
        super(customFieldValuePersister, stringConverter, selectConverter, optionsManager, genericConfigManager);
    }
}

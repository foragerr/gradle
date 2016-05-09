/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.announce.internal;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.plugins.announce.AnnouncePluginExtension;
import org.gradle.api.plugins.announce.Announcer;
import org.gradle.api.plugins.announce.internal.jdk6.AppleScriptBackedGrowlAnnouncer;
import org.gradle.internal.os.OperatingSystem;

public class DefaultAnnouncerFactory implements AnnouncerFactory {
    public DefaultAnnouncerFactory(AnnouncePluginExtension announcePluginConvention, ProcessOperations processOperations, IconProvider iconProvider) {
        this.announcePluginConvention = announcePluginConvention;
        this.iconProvider = iconProvider;
        this.processOperations = processOperations;
    }

    public Announcer createAnnouncer(String type) {
        Announcer announcer = createActualAnnouncer(type);
        return DefaultGroovyMethods.asBoolean(announcer) ? new IgnoreUnavailableAnnouncer(announcer) : new UnknownAnnouncer();
    }

    private Announcer createActualAnnouncer(String type) {
        if (StringGroovyMethods.isCase("local", type)) {
            if (OperatingSystem.current().isWindows()) {
                return createActualAnnouncer("snarl");
            } else if (OperatingSystem.current().isMacOsX()) {
                return createActualAnnouncer("growl");
            } else {
                return createActualAnnouncer("notify-send");
            }

        } else if (StringGroovyMethods.isCase("twitter", type)) {
            String username = announcePluginConvention.getUsername();
            String password = announcePluginConvention.getPassword();
            return new Twitter(username, password);
        } else if (StringGroovyMethods.isCase("notify-send", type)) {
            return new NotifySend(processOperations, iconProvider);
        } else if (StringGroovyMethods.isCase("snarl", type)) {
            return new Snarl(iconProvider);
        } else if (StringGroovyMethods.isCase("growl", type)) {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                try {
                    return new AppleScriptBackedGrowlAnnouncer(iconProvider);
                } catch (AnnouncerUnavailableException e) {
                    // Ignore and fall back to growl notify
                }

            }

            return new GrowlNotifyBackedAnnouncer(processOperations, iconProvider);
        } else {
            return null;
        }
    }

    private final AnnouncePluginExtension announcePluginConvention;
    private final IconProvider iconProvider;
    private final ProcessOperations processOperations;
}


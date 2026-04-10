/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.business;

import org.apache.roller.weblogger.business.url.AuthURLStrategy;
import org.apache.roller.weblogger.business.url.WeblogURLStrategy;
import org.apache.roller.weblogger.business.url.SearchURLStrategy;
import org.apache.roller.weblogger.business.url.MediaURLStrategy;
import org.apache.roller.weblogger.business.url.FeedURLStrategy;


/**
 * An interface representing the Roller Planet url strategy.
 *
 * Implementations of this interface provide methods which can be used to form
 * all of the public urls used by Roller Planet.
 */
public interface URLStrategy extends AuthURLStrategy, WeblogURLStrategy, SearchURLStrategy, MediaURLStrategy, FeedURLStrategy {
    
    /**
     * Get a version of this url strategy meant for use in previewing and set
     * it to preview a given theme.
     */
    URLStrategy getPreviewURLStrategy(String previewTheme);
}

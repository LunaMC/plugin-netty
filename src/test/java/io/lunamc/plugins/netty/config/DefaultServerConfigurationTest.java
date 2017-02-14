/*
 *  Copyright 2017 LunaMC.io
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.lunamc.plugins.netty.config;

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

public class DefaultServerConfigurationTest {

    @Test
    public void testLoad() throws Throwable {
        ServerConfiguration configuration;
        try (InputStream inputStream = getClass().getResourceAsStream("/example-server.xml")) {
            configuration = DefaultServerConfiguration.load(inputStream);
        }
        Assert.assertEquals(1234, configuration.getPort());
        Assert.assertEquals(12, configuration.getTimeout());
    }
}

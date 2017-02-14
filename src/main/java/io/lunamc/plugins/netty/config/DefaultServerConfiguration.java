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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.InputStream;

@XmlRootElement(namespace = "http://lunamc.io/server/1.0", name = "server")
@XmlAccessorType(XmlAccessType.FIELD)
public class DefaultServerConfiguration implements ServerConfiguration {

    private static final int DEFAULT_PORT = 25565;
    private static final int DEFAULT_TIMEOUT = 30;

    @XmlElement(namespace = "http://lunamc.io/server/1.0", name = "port")
    private int port = DEFAULT_PORT;

    @XmlElement(namespace = "http://lunamc.io/server/1.0", name = "timeout")
    private int timeout = DEFAULT_TIMEOUT;

    @Override
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public static DefaultServerConfiguration load(InputStream input) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(DefaultServerConfiguration.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return (DefaultServerConfiguration) unmarshaller.unmarshal(input);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }
}

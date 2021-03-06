/*
 * Copyright 2010 JBoss, a divison Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.errai.samples.queryservice.client.local;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.ui.*;

import org.errai.samples.queryservice.client.shared.QueryServiceRemote;
import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.bus.client.api.base.MessageBuilder;

public class QueryWidget extends Composite {
  @SuppressWarnings({"GwtUiHandlerErrors"})
  @UiHandler("sendQuery")
  void doSubmit(ClickEvent event) {

    MessageBuilder.createCall(new RemoteCallback<String[]>() {
      public void callback(String[] response) {
        if (response == null) {
          response = new String[]{"No results."};
        }

        /**
         * Build an HTML unordered list based on the results.
         */
        StringBuffer buf = new StringBuffer("<ul>");
        for (String result : response) {
          buf.append("<li>").append(result).append("</li>");
        }
        results.setHTML(buf.append("</ul>").toString());
      }
    }, QueryServiceRemote.class).query(queryBox.getText());
  }

  /**
   * Do boilerplate for UIBinder
   */
  @UiTemplate("QueryWidget.ui.xml")
  interface Binder extends UiBinder<Panel, QueryWidget> {
  }

  private static final Binder binder = GWT.create(Binder.class);

  {
    initWidget(binder.createAndBindUi(this));
  }

  @UiField
  TextBox queryBox;

  @UiField
  HTML results;
  @UiField
  Button sendQuery;
}




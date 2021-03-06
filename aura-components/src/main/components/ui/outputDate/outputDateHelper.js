/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
({
    formatDate: function(component) {
        var config = {
            langLocale : component.get("v.langLocale"),
            format : component.get("v.format"),
            timezone : component.get("v.timezone"),
            validateString : true
        };

        var helper = this;
        var displayValue = function (returnValue) {
            helper.setOutputValue(component, returnValue);
        };

        var value = component.get("v.value");
        this.dateTimeLib.dateTimeService.getDisplayValue(value, config, displayValue);
    },

    setOutputValue: function(component, displayValue) {
        var outputElement = component.find("span").getElement();
        if (!$A.util.isUndefinedOrNull(outputElement)) {
            var textContent = displayValue ? $A.localizationService.translateToLocalizedDigits(displayValue) : "";
            outputElement.textContent = textContent;

            // I think this was added for older versions of IE that didn't support textContent.
            outputElement.innerText = textContent;
        }
    }
})// eslint-disable-line semi
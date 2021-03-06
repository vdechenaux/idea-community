/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PrefixMatchingWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, @NotNull final CompletionLocation location) {
    final String prefix = item.getPrefixMatcher().getPrefix();

    if (prefix.isEmpty()) {
      return 0;
    }

    final String lookupString = item.getLookupString();
    final String prefixHumps = StringUtil.capitalsOnly(prefix);
    final String itemHumps = StringUtil.capitalsOnly(lookupString);

    if (itemHumps.startsWith(prefixHumps)) return 100 - itemHumps.length();

    if (lookupString.startsWith(prefix)) return 5;
    if (StringUtil.startsWithIgnoreCase(lookupString, prefix)) return 1;
    return 0;
  }
}

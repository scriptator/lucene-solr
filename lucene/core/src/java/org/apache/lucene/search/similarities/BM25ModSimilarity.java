/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search.similarities;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modification of the classical BM25 similarity measure:
 * <p>
 * TODO
 * Your task is to modify the score calculation in this BM25Similarity class
 * and create a new similarity class called BM25ModSimilarity. You then use it
 * in your index by modifying the configurations.
 * <p>
 * for 15 points: Lv, Yuanhua, and ChengXiang Zhai. ”When documents are very long, bm25 fails!.” Proceedings of SIGIR 2011. link
 * for 30 points: Lipani, Aldo, et al. ”Verboseness Fission for BM25 Document Length Normal- ization.” Proceedings of ICTIR 2015. link
 */
public class BM25ModSimilarity extends BM25Similarity {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public BM25ModSimilarity(float k1, float b) {
    super(k1, b);
    log.info("Hello World!");
  }

  public BM25ModSimilarity() {
  }

}

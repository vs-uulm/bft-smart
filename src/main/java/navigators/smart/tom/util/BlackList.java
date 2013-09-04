/*
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
 * and the authors indicated in the @author tags 
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
package navigators.smart.tom.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 
 * @author Christian Spann
 *
 */
public class BlackList {
	
//	private final int n;
	private final int f;
	private final Queue<Integer> blacklist = new LinkedList<Integer>();
	private final Set<Integer> whitelist = new HashSet<Integer>();

	public BlackList(int n, int f) {
//		this.n = n;
		this.f = f;
		for (int i = 0; i < n; i++) {
			whitelist.add(i);
		}
	}

	public List<Integer> getCorrect() {
		return new ArrayList<Integer>(whitelist);
	}

	public void addFirst(Integer badserver) {
		whitelist.remove(badserver);
		blacklist.add(badserver);
		if(blacklist.size()>f){
			whitelist.add(blacklist.poll());
		}
		
	}

}

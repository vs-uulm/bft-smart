/* * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
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
 */package navigators.smart.statemanagment;

import java.io.Serializable;
import java.util.Arrays;

/**
 *
 * @author Joao Sousa
 */
public class BatchInfo implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 7251994215265951672L;
	public final byte[] batch;
    public final Integer round;
    public final int leader;


    public BatchInfo () {
        this.batch = null;
        this.round = null;
        this.leader = -1;
    }
    public BatchInfo(byte[] batch, Integer round, int leader) {
        this.batch = batch;
        this.round = round;
        this.leader = leader;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BatchInfo) {
            BatchInfo bi = (BatchInfo) obj;
            return Arrays.equals(this.batch, bi.batch) && this.round.equals(bi.round) && this.leader == bi.leader;
        }
        return false;
    }

    @Override
    public int hashCode() {

        int hash = 1;

        if (this.batch != null) {
            for (int j = 0; j < this.batch.length; j++)
                hash = hash * 31 + this.batch[j];
        } else {
            hash = hash * 31 + 0;
        }

        hash = hash * 31 + this.round.intValue();
        hash = hash * 31 + this.leader;

        return hash;
    }
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "BatchInfo [batch=" + Arrays.toString(batch) + ", leader=" + leader + ", round=" + round + "]";
	}
}

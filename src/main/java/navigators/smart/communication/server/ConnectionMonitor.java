/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt. If not, see <http://www.gnu.org/licenses/>.
 */
package navigators.smart.communication.server;

/**
 * This interface describes a Monitor that is informed when a connection
 * is made to the Publisher, where it is registered.
 * 
 * @author Christian Spann 
 */
public interface ConnectionMonitor {
	
	/**
	 * This method is called, when a connection with the replica with
	 * the given ID is established
	 * @param id The id of the connected replica.
	 */
	public void connected(Integer id);
	
}

/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.network.serverpackets;

import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

/**
 * This class ...
 *
 * @version $Revision: 1.2.2.1.2.3 $ $Date: 2005/03/27 15:29:57 $
 */
public class PrivateStoreMsgSell extends L2GameServerPacket
{
	private static final String _S__B5_PRIVATESTOREMSGSELL = "[S] a2 PrivateStoreMsgSell";
	private L2PcInstance _activeChar;
	private String _storeMsg;

	public PrivateStoreMsgSell(L2PcInstance player)
	{
		_activeChar = player;
		if (_activeChar.getSellList() != null)
			_storeMsg = _activeChar.getSellList().getTitle();
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0xa2);
		writeD(_activeChar.getObjectId());
		writeS(_storeMsg);
	}

	/* (non-Javadoc)
	 * @see net.sf.l2j.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__B5_PRIVATESTOREMSGSELL;
	}
}
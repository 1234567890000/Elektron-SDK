/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license      --
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
 *|                See the project's LICENSE.md for details.                  --
 *|           Copyright Thomson Reuters 2015. All rights reserved.            --
 *|-----------------------------------------------------------------------------
 */

#include "OmmProviderEvent.h"
#include "ItemCallbackClient.h"

using namespace thomsonreuters::ema::access;

OmmProviderEvent::OmmProviderEvent() :
 _pItem( 0 )
{
}

OmmProviderEvent::~OmmProviderEvent()
{
}

UInt64 OmmProviderEvent::getHandle() const
{
	return (UInt64)_pItem;
}

void* OmmProviderEvent::getClosure() const
{
	return _pItem->getClosure();
}

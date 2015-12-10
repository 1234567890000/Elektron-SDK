/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license      --
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
 *|                See the project's LICENSE.md for details.                  --
 *|           Copyright Thomson Reuters 2015. All rights reserved.            --
 *|-----------------------------------------------------------------------------
 */

#include "OmmConsumerActiveConfig.h"

using namespace thomsonreuters::ema::access;

OmmConsumerActiveConfig::OmmConsumerActiveConfig() : ActiveConfig(), consumerName()
{
}

OmmConsumerActiveConfig::~OmmConsumerActiveConfig()
{
}

void OmmConsumerActiveConfig::clear()
{
	consumerName.clear();
	ActiveConfig::clear();
}


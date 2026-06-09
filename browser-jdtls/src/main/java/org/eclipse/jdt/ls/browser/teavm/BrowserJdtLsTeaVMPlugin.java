package org.eclipse.jdt.ls.browser.teavm;

import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

public final class BrowserJdtLsTeaVMPlugin implements TeaVMPlugin {

	@Override
	public void install(TeaVMHost host) {
		host.add(new EcjMessagesTransformer());
	}
}

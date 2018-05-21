// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Optional;
import org.jgroups.JChannel;
import org.jgroups.protocols.BARRIER;
import org.jgroups.protocols.FD_ALL;
import org.jgroups.protocols.FD_SOCK;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.protocols.FRAG2;
import org.jgroups.protocols.MERGE3;
import org.jgroups.protocols.MFC;
import org.jgroups.protocols.RSVP;
import org.jgroups.protocols.UDP;
import org.jgroups.protocols.UFC;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.VERIFY_SUSPECT;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.protocols.pbcast.STATE_TRANSFER;
import org.jgroups.stack.ProtocolStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class JChannelProvider implements Provider<JChannel> {
  private static final Logger log = LoggerFactory.getLogger(JChannelProvider.class);

  private final Configuration.JGroups jgroupsConfig;
  private final Path sharedFolder;

  @Inject
  JChannelProvider(Configuration pluginConfiguration) {
    this.jgroupsConfig = pluginConfiguration.jgroups();
    this.sharedFolder = pluginConfiguration.main().sharedDirectory();
  }

  @Override
  public JChannel get() {
    Optional<Path> protocolStack = jgroupsConfig.protocolStack();
    try {
      if (protocolStack.isPresent()) {
        return new JChannel(protocolStack.get().toString());
      }
      log.info("No protocol stack found. Using pre-defined stack");
      return getPreDefinedJChannel();
    } catch (Exception e) {
      throw new ProvisionException(
          String.format(
              "Unable to create a channel with protocol stack: %s",
              protocolStack.isPresent() ? protocolStack : "pre-defined"),
          e);
    }
  }

  private JChannel getPreDefinedJChannel() throws Exception {
    JChannel jChannel = new JChannel(false);
    ProtocolStack protocolStack = new ProtocolStack();
    jChannel.setProtocolStack(protocolStack);
    protocolStack
        .addProtocol(
            new UDP()
                .setValue("ip_mcast", false)
                .setValue("mcast_port", 45588)
                .setValue("ip_ttl", 4)
                .setValue("tos", 8)
                .setValue("ucast_recv_buf_size", 5_000_000)
                .setValue("ucast_send_buf_size", 5_000_000)
                .setValue("mcast_recv_buf_size", 5_000_000)
                .setValue("mcast_send_buf_size", 5_000_000)
                .setValue("max_bundle_size", 64000)
                .setValue("max_bundle_timeout", 30)
                .setValue("enable_diagnostics", true)
                .setValue("thread_naming_pattern", "cl")
                .setValue("timer_type", "new3")
                .setValue("timer_min_threads", 2)
                .setValue("timer_max_threads", 4)
                .setValue("timer_keep_alive_time", 3000)
                .setValue("timer_queue_max_size", 500)
                .setValue("thread_pool_enabled", true)
                .setValue("thread_pool_min_threads", 2)
                .setValue("thread_pool_max_threads", 8)
                .setValue("thread_pool_keep_alive_time", 5000)
                .setValue("thread_pool_queue_enabled", true)
                .setValue("thread_pool_queue_max_size", 10000)
                .setValue("thread_pool_rejection_policy", "discard")
                .setValue("oob_thread_pool_enabled", true)
                .setValue("oob_thread_pool_min_threads", 1)
                .setValue("oob_thread_pool_max_threads", 8)
                .setValue("oob_thread_pool_keep_alive_time", 5000)
                .setValue("oob_thread_pool_queue_enabled", false)
                .setValue("oob_thread_pool_queue_max_size", 100)
                .setValue("oob_thread_pool_rejection_policy", "discard"))
        .addProtocol(
            new FILE_PING()
                .setValue("interval", 1000)
                .setValue("location", sharedFolder.toString())
                .setValue("remove_old_coords_on_view_change", true)
                .setValue("remove_all_files_on_view_change", true))
        .addProtocol(new MERGE3().setValue("max_interval", 30000).setValue("min_interval", 10000))
        .addProtocol(new FD_SOCK())
        .addProtocol(new FD_ALL())
        .addProtocol(new VERIFY_SUSPECT().setValue("timeout", 1500))
        .addProtocol(new BARRIER())
        .addProtocol(
            new NAKACK2()
                .setValue("xmit_interval", 500)
                .setValue("xmit_table_num_rows", 100)
                .setValue("xmit_table_msgs_per_row", 2000)
                .setValue("xmit_table_max_compaction_time", 30000)
                .setValue("max_msg_batch_size", 500)
                .setValue("use_mcast_xmit", false)
                .setValue("discard_delivered_msgs", true))
        .addProtocol(
            new UNICAST3()
                .setValue("xmit_interval", 500)
                .setValue("xmit_table_num_rows", 100)
                .setValue("xmit_table_msgs_per_row", 2000)
                .setValue("xmit_table_max_compaction_time", 60000)
                .setValue("max_msg_batch_size", 500)
                .setValue("conn_expiry_timeout", 0))
        .addProtocol(
            new STABLE()
                .setValue("stability_delay", 1000)
                .setValue("desired_avg_gossip", 50000)
                .setValue("max_bytes", 4_000_000))
        .addProtocol(
            new GMS()
                .setValue("print_local_addr", true)
                .setValue("join_timeout", 2000)
                .setValue("view_bundling", true))
        .addProtocol(new UFC().setValue("max_credits", 2_000_000).setValue("min_threshold", 0.4))
        .addProtocol(new MFC().setValue("max_credits", 2_000_000).setValue("min_threshold", 0.4))
        .addProtocol(new FRAG2().setValue("frag_size", 60000))
        .addProtocol(new RSVP().setValue("resend_interval", 2000).setValue("timeout", 10000))
        .addProtocol(new STATE_TRANSFER());
    protocolStack.init();
    return jChannel;
  }
}

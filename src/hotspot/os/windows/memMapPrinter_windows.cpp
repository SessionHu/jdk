/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"

#include "nmt/memMapPrinter.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#include <limits.h>

#include <iostream>
//#include <Windows.h>
#include <winnt.h>
#include <memoryapi.h>
#include <psapi.h>
#include <tchar.h>

class ProcSmapsInfo {
    static const int MAX_STR_LEN = 20;
    HANDLE hProcess;
public:
    char apBuffer[MAX_STR_LEN];
    char stateBuffer[20];
    char protectBuffer[20];
    char typeBuffer[20];
    int rss;
    char fileName[100];
    ProcSmapsInfo() {
        hProcess = GetCurrentProcess();
    }
    void process(MEMORY_BASIC_INFORMATION& mInfo) {
        getProtectString(apBuffer, mInfo.AllocationProtect);
        getStateString(stateBuffer, mInfo);
        getProtectString(protectBuffer, mInfo.Protect);
        getTypeString(typeBuffer, mInfo);
        fileName[0] = 0;
        if (mInfo.Type == MEM_IMAGE) {
           // HMODULE hModule = (HMODULE)mInfo.AllocationBase;
            HMODULE hModule = 0;
            if (!GetModuleHandleEx(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS, static_cast<LPCSTR>(mInfo.AllocationBase), &hModule)) {
                ;//
            }
            if (hModule == 0 || !GetModuleFileName(hModule, fileName, sizeof(fileName) / sizeof(char))) {
                fileName[0] = 0;
            }
        }
        rss = 0;

    }

    const char* getProtectString(char buffer[20], DWORD prot) {
        const int IR = 0;
        const int IW = 1;
        const int IX = 2;
        const int IP = 3;
        int idx = 4;
        strncpy_s(buffer, sizeof(buffer), "----  ", 7);
        if (prot & PAGE_EXECUTE) {
            buffer[IX] = 'x';
        } else if (prot & PAGE_EXECUTE_READ) {
            buffer[IR] = 'r';
            buffer[IX] = 'x';
        } else if (prot & PAGE_EXECUTE_READWRITE) {
            buffer[IR] = 'r';
            buffer[IW] = 'w';
            buffer[IX] = 'x';
        } else if (prot & PAGE_EXECUTE_WRITECOPY) {
            buffer[IW] = 'w';
            buffer[IX] = 'x';
            buffer[idx++] = 'c';
        } else if (prot & PAGE_NOACCESS) {
            strncpy_s(buffer, sizeof(buffer), "(NA)", 5);
        } else if (prot & PAGE_READONLY) {
            buffer[IR] = 'r';
        } else if (prot & PAGE_READWRITE) {
            buffer[IR] = 'r';
            buffer[IW] = 'w';
        } else if (prot & PAGE_WRITECOPY) {
            buffer[IW] = 'w';
            buffer[idx++] = 'c';
        } else if (prot & PAGE_TARGETS_INVALID) {
            buffer[idx++] = 'i';
        } else if (prot & PAGE_TARGETS_NO_UPDATE) {
            buffer[idx++] = 'n';
        } else if (prot != 0) {
            snprintf(buffer, sizeof(buffer), "(0x%x)", prot);
            idx = static_cast<int>(strlen(buffer));
        }

        if (prot & PAGE_GUARD) {
            buffer[idx++] = 'G';
        }
        if (prot & PAGE_NOCACHE) {
            buffer[idx++] = 'C';
        }
        if (prot & PAGE_WRITECOMBINE) {
            buffer[idx++] = 'W';
        }
        if (idx == 0) {
            snprintf(buffer, sizeof(buffer), "0x%x", prot);
        } else {
            buffer[idx] = 0;
        }
        return buffer;
    }
    const char* getStateString(char buffer[20], MEMORY_BASIC_INFORMATION& mInfo) {
        if (mInfo.State == MEM_COMMIT) {
            buffer[0] = 'c'; buffer[1] = 0;
        } else if (mInfo.State == MEM_FREE) {
            buffer[0] = 'f'; buffer[1] = 0;
        } else if (mInfo.State == MEM_RESERVE) {
            buffer[0] = 'r'; buffer[1] = 0;
        } else {
            snprintf(buffer, sizeof(buffer), "0x%x", mInfo.State);
        }
        return buffer;
    }

    const char* getTypeString(char buffer[20], MEMORY_BASIC_INFORMATION& mInfo) {
        if (mInfo.Type == MEM_IMAGE) {
            strncpy_s(buffer, sizeof(buffer), "img", 4);
        } else if (mInfo.Type == MEM_MAPPED) {
            strncpy_s(buffer, sizeof(buffer), "map", 4);
        } else if (mInfo.Type == MEM_PRIVATE) {
            strncpy_s(buffer, sizeof(buffer), "prv", 4);
        } else {
            snprintf(buffer, sizeof(buffer), "0x%x", mInfo.Type);
        }
        return buffer;
    }
};

class ProcSmapsSummary {
  APP_MEMORY_INFORMATION _meminfo;
  unsigned _kernel_page_size;
  unsigned _num_mappings;
  bool _meminfo_valid;
  size_t _total_region_size;  // combined resident set size
  size_t _total_committed;    // combined committed size
  size_t _total_reserved;     // combined shared size
public:
  ProcSmapsSummary() : _num_mappings(0),  _total_region_size(0),
                       _kernel_page_size(0), _meminfo_valid(false) {
    HANDLE hProcess = GetCurrentProcess();
    _meminfo_valid = GetProcessInformation(hProcess, ProcessAppMemoryInfo, &_meminfo, sizeof(_meminfo));
    SYSTEM_INFO sysInfo;
    GetSystemInfo(&sysInfo);
    _kernel_page_size = sysInfo.dwPageSize;
  }
  void add_mapping(const MEMORY_BASIC_INFORMATION& mInfo, const ProcSmapsInfo& info) {
    if (mInfo.State != MEM_FREE) {
      _num_mappings++;
      _total_region_size += mInfo.RegionSize;
      _total_committed += mInfo.State == MEM_COMMIT ? mInfo.RegionSize : 0;
      _total_reserved += mInfo.State == MEM_RESERVE ? mInfo.RegionSize : 0;
    }
  }

  void print_on(const MappingPrintSession& session) const {
    outputStream* st = session.out();
    st->print_cr("Number of mappings: %u", _num_mappings);
    st->print_cr("          pagesize: %u", _kernel_page_size);
    if (_meminfo_valid) {
      st->print_cr("   AvailableCommit: %zu (" PROPERFMT ")", _meminfo.AvailableCommit, PROPERFMTARGS(_meminfo.AvailableCommit));
      st->print_cr("PrivateCommitUsage: %zu (" PROPERFMT ")", _meminfo.PrivateCommitUsage, PROPERFMTARGS(_meminfo.PrivateCommitUsage));
      st->print_cr(" PeakPrivateCommit: %zu (" PROPERFMT ")", _meminfo.PeakPrivateCommitUsage, PROPERFMTARGS(_meminfo.PeakPrivateCommitUsage));
      st->print_cr("  TotalCommitUsage: %zu (" PROPERFMT ")", _meminfo.TotalCommitUsage, PROPERFMTARGS(_meminfo.TotalCommitUsage));
    }
    st->print_cr(" total region size: %zu (" PROPERFMT ")", _total_region_size, PROPERFMTARGS(_total_region_size));
    st->print_cr("         committed: %zu (" PROPERFMT ")", _total_committed, PROPERFMTARGS(_total_committed));
    st->print_cr("          reserved: %zu (" PROPERFMT ")", _total_reserved, PROPERFMTARGS(_total_reserved));
  }
};

class ProcSmapsPrinter {
  const MappingPrintSession& _session;
public:
  ProcSmapsPrinter(const MappingPrintSession& session) :
    _session(session)
  {}

  void print_single_mapping(const MEMORY_BASIC_INFORMATION& mInfo, const ProcSmapsInfo& info) const {
    outputStream* st = _session.out();
#define INDENT_BY(n)          \
  if (st->fill_to(n) == 0) {  \
    st->print(" ");           \
  }
    if (mInfo.State == MEM_FREE) {
      st->print(PTR_FORMAT "-" PTR_FORMAT, mInfo.BaseAddress, static_cast<const char*>(mInfo.BaseAddress) + mInfo.RegionSize);
      INDENT_BY(38);
      st->print("%12zu", mInfo.RegionSize);
      INDENT_BY(51);
      st->print("%s", "-free-");
    } else {
      st->print(PTR_FORMAT "-" PTR_FORMAT, mInfo.BaseAddress, static_cast<const char*>(mInfo.BaseAddress) + mInfo.RegionSize);
      INDENT_BY(38);
      st->print("%12zu", mInfo.RegionSize);
      INDENT_BY(51);
      st->print("%s", info.protectBuffer);
      INDENT_BY(57);
      st->print("%s-%s", info.stateBuffer, info.typeBuffer);
      INDENT_BY(60);
      st->print("%6llx", reinterpret_cast<const unsigned long long>(mInfo.BaseAddress) - reinterpret_cast<const unsigned long long>(mInfo.AllocationBase));
      INDENT_BY(68);
      if (_session.print_nmt_info_for_region(mInfo.BaseAddress, static_cast<const char*>(mInfo.BaseAddress) + mInfo.RegionSize)) {
        st->print(" ");
      }
      st->print_raw(info.fileName);
    }
  #undef INDENT_BY
    st->cr();
  }

  void print_legend() const {
    outputStream* st = _session.out();
    st->print_cr("from, to, vsize: address range and size");
    st->print_cr("prot:            protection:");
    st->print_cr("                     rwx: read / write / execute");
    st->print_cr("                     c: copy on write");
    st->print_cr("                     G: guard");
    st->print_cr("                     i: targets invalid");
    st->print_cr("                     n: targets noupdate");
    st->print_cr("                     C: no cache");
    st->print_cr("                     W: write combine");
    st->print_cr("state/type:      region state and type");
    st->print_cr(" state:              committed / reserved");
    st->print_cr(" type:               image / mapped / private");
    st->print_cr("vm info:         VM information (requires NMT)");
    {
      streamIndentor si(st, 16);
      _session.print_nmt_flag_legend();
    }
    st->print_cr("file:            file mapped, if mapping is not anonymous");
  }

  void print_header() const {
    outputStream* st = _session.out();
    //            0         1         2         3         4         5         6         7         8         9         0         1         2         3         4         5         6         7
    //            012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
    //            0x0000000414000000-0x0000000453000000 123456789012 rw-p 123456789012 123456789012 16g  thp,thpadv       STACK-340754-Monitor-Deflation-Thread /shared/tmp.txt
    st->print_cr("from               to                        vsize prot  state/type   offset  vm info/file");
    st->print_cr("=============================================================================================================================");
  }
};

void MemMapPrinter::pd_print_all_mappings(const MappingPrintSession& session) {

    HANDLE hProcess = GetCurrentProcess();

    SYSTEM_INFO sysInfo;
    GetSystemInfo(&sysInfo);

    ProcSmapsPrinter printer(session);
    ProcSmapsSummary summary;

    outputStream* const st = session.out();

    printer.print_legend();
    st->cr();
    printer.print_header();

    MEMORY_BASIC_INFORMATION mInfo;
    ProcSmapsInfo info;

    for (char* ptr = 0; VirtualQueryEx(hProcess, ptr, &mInfo, sizeof(mInfo)) == sizeof(mInfo); ptr += mInfo.RegionSize) {
      info.process(mInfo);
      printer.print_single_mapping(mInfo, info);
      summary.add_mapping(mInfo, info);
      if (ptr != mInfo.BaseAddress) {
          printf("XXXX\n");
      }
      if (mInfo.PartitionId != 0) {
          printf("patrition = 0x%x\n", mInfo.PartitionId);
      }
    }
    st->cr();
    summary.print_on(session);
    st->cr();
}

// Copyright (c) 2012, Cornell University
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
//     * Redistributions of source code must retain the above copyright notice,
//       this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of HyperDex nor the names of its contributors may be
//       used to endorse or promote products derived from this software without
//       specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

#define __STDC_LIMIT_MACROS

// C
#include <cassert>
#include <cmath>
#include <cstdlib>

// Linux
#include <ieee754.h>

// C++
#include <iostream>

// e
#include <e/endian.h>

// HyperDex
#include "daemon/index_encode.h"

// We flip the sign bit and shift appropriately to make sure that INT64_MIN
// corresponds to 0 and INT64_MAX corresponds to UINT64_MAX.
char*
hyperdex :: index_encode_int64(int64_t in, char* ptr)
{
    uint64_t out = static_cast<uint64_t>(in);
    out += in >= 0 ? 0x8000000000000000ULL : INT64_MIN;
    return e::pack64be(out, ptr);
}

// A little reminder about IEEE 754 doubles.  Source Patterson&Hennessy 3ed.
//
// They have bits like:
//      S = sign * 1
//      E = exponent * 11
//      F = fraction * 52
//      SEEEEEEEEEEEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
//
// Where:
//      Exponent    Fraction        Object
//      0           0               0
//      0           Nonzero         +- subnormal
//      1-2046      anything        +- normal fp
//      2047        0               +- infinity
//      2047        Nonzero         NaN
//
// What we want to do is map this onto unsigned numbers such that x1 < x2
// implies the resulting bytestrings of length 8 are comparable.
//
// To that end, want to order things as follows
//      Sign    Exponent    Fraction        Object
//      -       2047        0               - infinity
//                                          sign=0
//                                          exp=0
//                                          frac=0
//                                          shift=0
//                                          The smallest number must come first
//      -       1-2046      anything        - normal fp
//                                          sign=0
//                                          exp=exp^0x7ff
//                                          frac=frac^0xfffffffffffff
//                                          shift=1
//      -       0           Nonzero         truncate to 0
//      -       0           0               0x8000000000000000 shift=1
//      +       0           0               0x8000000000000000 shift=1
//      +       0           Nonzero         truncate to 0
//      +       1-2046      anything        + normal fp
//                                          sign=1
//                                          exp=exp
//                                          frac=frac
//                                          shift=2
//      +       2047        0               + infinity
//                                          sign=1
//                                          exp=0x7ff
//                                          frac=0
//                                          shift=2
//      -       2047        Nonzero         NaN
//      +       2047        Nonzero         NaN
//                                          sign=1
//                                          exp=0x7ff
//                                          frac=0
//                                          shift=3
char*
hyperdex :: index_encode_double(double x, char* ptr)
{
    uint64_t out = 0xffffffffffffffffULL;

    if (isinf(x))
    {
        if (x > 0)
        {
            out = 0xfff0000000000000ULL + 2;
        }
        else
        {
            out = 0;
        }
    }
    else if (isnan(x))
    {
        out = 0xfff0000000000000ULL + 3;
    }
    else if (x == 0)
    {
        out = 0x8000000000000000ULL + 1;
    }
    else
    {
        ieee754_double d;
        d.d = x;
        uint64_t sign = d.ieee.negative ^ 0x1;
        uint64_t exp  = d.ieee.exponent;
        uint64_t frac = d.ieee.mantissa0;
        frac <<= 32;
        frac |= d.ieee.mantissa1;
        uint64_t shift = 2;

        if (x < 0)
        {
            exp  ^= 0x7ffULL;
            frac ^= 0xfffffffffffffULL;
            shift = 1;
        }

        out = (sign << 63) | (exp << 52) | (frac);
    }

    return e::pack64be(out, ptr);
}

void
hyperdex :: index_encode_bump(char* _ptr, char* _end)
{
    assert(_ptr);
    assert(_ptr < _end);
    uint8_t* ptr = reinterpret_cast<uint8_t*>(_end) - 1;
    uint8_t* end = reinterpret_cast<uint8_t*>(_ptr);

    for (; ptr >= end; --ptr)
    {
        if (*ptr < 255)
        {
            ++(*ptr);
            return;
        }
        else
        {
            *ptr = 0;
        }
    }

    abort();
}

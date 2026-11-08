# Minimal stand-in for Locale::Maketext::Simple (missing from Git for Windows' Perl), enough for Params::Check
# used by OpenSSL's Configure: loc() fills [_1]-style placeholders, no translation.
package Locale::Maketext::Simple;
use strict;
sub import {
    my $caller = caller;
    no strict 'refs';
    *{"${caller}::loc"} = sub { my ($s, @a) = @_; $s =~ s/\[_(\d+)\]/defined $a[$1-1] ? $a[$1-1] : ''/ge; $s =~ s/\[[^\]]*\]//g; return $s; };
    *{"${caller}::loc_lang"} = sub { };
}
1;

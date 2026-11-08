# Minimal stand-in for Pod::Usage (missing from Git for Windows' Perl): configdata.pm only needs pod2usage for --help.
package Pod::Usage;
use strict;
use Exporter 'import';
our @EXPORT = qw(pod2usage);
sub pod2usage { my %a = @_ == 1 ? (-exitval => $_[0]) : @_; exit(defined $a{-exitval} && $a{-exitval} =~ /^\d+$/ ? $a{-exitval} : 2); }
1;

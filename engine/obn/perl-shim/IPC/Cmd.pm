# Minimal stand-in for IPC::Cmd (Git for Windows' Perl lacks its ExtUtils::MakeMaker dependency).
# OpenSSL's Configure only calls can_run(): find an executable on PATH.
package IPC::Cmd;
use strict;
use File::Spec;
our @EXPORT_OK = qw(can_run);
sub import {}
sub can_run {
    my $name = shift; $name =~ s/\s.*$//;
    return $name if File::Spec->file_name_is_absolute($name) && -x $name;
    for my $dir (File::Spec->path) {
        for my $ext ('', '.exe', '.cmd', '.bat') { my $p = File::Spec->catfile($dir, $name . $ext); return $p if -f $p && -x _; }
    }
    return undef;
}
1;

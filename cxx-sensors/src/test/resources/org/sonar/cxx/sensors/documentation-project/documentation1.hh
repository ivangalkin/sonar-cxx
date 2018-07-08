#ifndef DOCUMENTATION1_H
#define DOCUMENTATION1_H

#include "documentation0.hh"

/**
 * pointer to metadata container
 */
using tag_ptr = unsigned char*;                            // 1

RC tag_init(tag_ptr t);                                    // 2 [undocumented]
RC tag_set_title(tag_ptr t, const char* title);            // 3 [undocumented]
RC tag_set_artist(tag_ptr t, const char* artist);          // 4 [undocumented]
RC tag_set_album(tag_ptr t, const char* album);            // 5 [undocumented]
RC tag_set_year(tag_ptr t, const char* year);              // 6 [undocumented]
RC tag_set_comment(tag_ptr t, const char* comment);        // 7 [undocumented]

#endif
